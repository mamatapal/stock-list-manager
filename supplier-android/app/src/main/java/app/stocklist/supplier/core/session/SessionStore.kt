package app.stocklist.supplier.core.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.stocklist.supplier.data.remote.MeDto
import app.stocklist.supplier.data.remote.SessionDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "supplier_session")

sealed interface SessionState {
    data object Loading : SessionState
    data class SignedOut(val reason: String? = null) : SessionState
    data class SignedIn(val me: MeDto) : SessionState
}

/**
 * Who is signed in. The refresh token is stored encrypted with a Keystore key; the 15-minute access token is kept in
 * memory only. The password is never stored.
 */
@Singleton
class SessionStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cipher: KeystoreCipher,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val keyRefresh = stringPreferencesKey("refresh_token_enc")
    private val keyMe = stringPreferencesKey("me_json")
    private val keyDevice = stringPreferencesKey("device_id")

    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    @Volatile
    var accessToken: String? = null
        private set

    suspend fun load() {
        val prefs = context.sessionDataStore.data.first()
        val refresh = prefs[keyRefresh]?.let { cipher.decrypt(it) }
        val me = prefs[keyMe]?.let { runCatching { json.decodeFromString(MeDto.serializer(), it) }.getOrNull() }
        _state.value = if (refresh != null && me != null) SessionState.SignedIn(me) else SessionState.SignedOut()
    }

    suspend fun save(session: SessionDto) {
        accessToken = session.accessToken
        context.sessionDataStore.edit {
            it[keyRefresh] = cipher.encrypt(session.refreshToken.toByteArray())
            it[keyMe] = json.encodeToString(MeDto.serializer(), session.me)
        }
        _state.value = SessionState.SignedIn(session.me)
    }

    suspend fun updateMe(me: MeDto) {
        context.sessionDataStore.edit { it[keyMe] = json.encodeToString(MeDto.serializer(), me) }
        if (_state.value is SessionState.SignedIn) _state.value = SessionState.SignedIn(me)
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
            it.remove(keyMe)
        }
        _state.value = SessionState.SignedOut(reason)
    }

    fun clearBlocking(reason: String?) = runBlocking { clear(reason) }

    /** Stable random id for this installation, sent with sign-in. */
    suspend fun deviceId(): String {
        context.sessionDataStore.data.first()[keyDevice]?.let { return it }
        val id = UUID.randomUUID().toString()
        context.sessionDataStore.edit { it[keyDevice] = id }
        return id
    }
}
