package app.stocklist.app.core.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.stocklist.app.data.remote.MeDto
import app.stocklist.app.data.remote.SessionDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

sealed interface SessionState {
    data object Loading : SessionState
    data class SignedOut(val reason: String? = null) : SessionState
    data class SignedIn(val user: MeDto) : SessionState
}

/**
 * Holds who is signed in. The refresh token and database key are stored encrypted with a Keystore key;
 * the 15-minute access token is kept in memory only.
 */
@Singleton
class SessionStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cipher: KeystoreCipher,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val keyRefresh = stringPreferencesKey("refresh_token_enc")
    private val keyUser = stringPreferencesKey("user_json")
    private val keyDevice = stringPreferencesKey("device_id")
    private val keyDb = stringPreferencesKey("db_key_enc")

    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    @Volatile
    var accessToken: String? = null
        private set

    suspend fun load() {
        val prefs = context.sessionDataStore.data.first()
        val refresh = prefs[keyRefresh]?.let { cipher.decrypt(it) }
        val user = prefs[keyUser]?.let { runCatching { json.decodeFromString(MeDto.serializer(), it) }.getOrNull() }
        _state.value = if (refresh != null && user != null) SessionState.SignedIn(user) else SessionState.SignedOut()
    }

    suspend fun save(session: SessionDto) {
        accessToken = session.accessToken
        context.sessionDataStore.edit {
            it[keyRefresh] = cipher.encrypt(session.refreshToken.toByteArray())
            it[keyUser] = json.encodeToString(MeDto.serializer(), session.user)
        }
        _state.value = SessionState.SignedIn(session.user)
    }

    suspend fun updateUser(user: MeDto) {
        context.sessionDataStore.edit { it[keyUser] = json.encodeToString(MeDto.serializer(), user) }
        if (_state.value is SessionState.SignedIn) _state.value = SessionState.SignedIn(user)
    }

    suspend fun refreshToken(): String? =
        context.sessionDataStore.data.first()[keyRefresh]?.let { cipher.decrypt(it) }?.let { String(it) }

    /** Called from OkHttp's authenticator thread. */
    fun refreshTokenBlocking(): String? = runBlocking { refreshToken() }

    fun saveBlocking(session: SessionDto) = runBlocking { save(session) }

    suspend fun clear(reason: String? = null) {
        accessToken = null
        context.sessionDataStore.edit {
            it.remove(keyRefresh)
            it.remove(keyUser)
        }
        _state.value = SessionState.SignedOut(reason)
    }

    fun clearBlocking(reason: String?) = runBlocking { clear(reason) }

    /** Stable random id for this installation; sent with sign-in so the owner can see sessions per phone. */
    suspend fun deviceId(): String {
        context.sessionDataStore.data.first()[keyDevice]?.let { return it }
        val id = UUID.randomUUID().toString()
        context.sessionDataStore.edit { it[keyDevice] = id }
        return id
    }

    /** 32 random bytes for SQLCipher, created once and stored encrypted. */
    suspend fun databaseKey(): ByteArray {
        context.sessionDataStore.data.first()[keyDb]?.let { enc -> cipher.decrypt(enc)?.let { return it } }
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        context.sessionDataStore.edit { it[keyDb] = cipher.encrypt(key) }
        return key
    }
}
