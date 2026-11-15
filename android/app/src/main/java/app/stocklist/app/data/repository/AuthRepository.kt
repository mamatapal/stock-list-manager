package app.stocklist.app.data.repository

import android.os.Build
import app.stocklist.app.BuildConfig
import app.stocklist.app.core.session.SessionStore
import app.stocklist.app.core.session.StepUpCache
import app.stocklist.app.data.local.AppDatabase
import app.stocklist.app.data.local.LocalMeta
import app.stocklist.app.data.remote.ApiError
import app.stocklist.app.data.remote.ApiResult
import app.stocklist.app.data.remote.ApiService
import app.stocklist.app.data.remote.AuthApi
import app.stocklist.app.data.remote.ChangePasswordRequest
import app.stocklist.app.data.remote.ForgotPasswordRequest
import app.stocklist.app.data.remote.LoginRequest
import app.stocklist.app.data.remote.LogoutRequest
import app.stocklist.app.data.remote.ReauthRequest
import app.stocklist.app.data.remote.RegisterBusinessRequest
import app.stocklist.app.data.remote.ResetPasswordRequest
import app.stocklist.app.data.remote.SessionDto
import app.stocklist.app.data.remote.apiCall
import app.stocklist.app.data.remote.apiCallUnit
import app.stocklist.app.data.sync.SyncScheduler
import app.stocklist.app.data.sync.UnsentChanges
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class RegisterForm(
    val businessName: String,
    val ownerName: String,
    val username: String,
    val mobile: String,
    val email: String,
    val password: String,
    val address: String,
    val city: String,
    val state: String,
    val pinCode: String,
    val gstin: String,
)

/** Error code: this phone holds another person's unsent changes (see [AuthRepository.login]). */
const val UNSENT_CHANGES = "UNSENT_CHANGES"

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val api: ApiService,
    private val session: SessionStore,
    private val stepUp: StepUpCache,
    private val database: Lazy<AppDatabase>,
    private val syncScheduler: SyncScheduler,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) {
    /** PDFs made for the previous user carry their business's figures: removed with the database. */
    private fun clearPdfs() {
        java.io.File(context.cacheDir, PdfRepository.PDF_DIR).deleteRecursively()
    }

    private val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".take(100)

    suspend fun register(f: RegisterForm): ApiResult<Unit> {
        val result = apiCall {
            authApi.register(
                RegisterBusinessRequest(
                    businessName = f.businessName.trim(), ownerName = f.ownerName.trim(),
                    username = f.username.trim(), mobile = f.mobile.trim(), email = f.email.trim(),
                    password = f.password, address = f.address.trim(), city = f.city.trim(),
                    state = f.state.trim(), pinCode = f.pinCode.trim(),
                    gstin = f.gstin.trim().uppercase().ifBlank { null },
                    deviceId = session.deviceId(), deviceName = deviceName, appVersion = BuildConfig.VERSION_NAME,
                ),
            )
        }
        return afterSignIn(result)
    }

    /**
     * [role] is "OWNER" or "STAFF", from the sign-in screen used. When this phone still holds unsent changes made by
     * someone else, signing in as a different person would delete them: the call then fails with code
     * [UNSENT_CHANGES] (nothing is deleted) unless [discardOthersChanges] is true.
     */
    suspend fun login(username: String, password: String, role: String, discardOthersChanges: Boolean = false): ApiResult<Unit> {
        val result = apiCall {
            authApi.login(
                LoginRequest(username.trim(), password, session.deviceId(), deviceName, BuildConfig.VERSION_NAME, role),
            )
        }
        return afterSignIn(result, discardOthersChanges)
    }

    /**
     * Local data always belongs to exactly one user of one business. Signing in as anyone else starts clean, so
     * one person's unsent changes are never sent under another person's name.
     */
    private suspend fun afterSignIn(result: ApiResult<SessionDto>, discardOthersChanges: Boolean = false): ApiResult<Unit> =
        when (result) {
            is ApiResult.Failure -> result
            is ApiResult.Ok -> {
                val blocked = withContext(Dispatchers.IO) {
                    val db = database.get()
                    val owner = "${result.value.user.businessId}/${result.value.user.userId}"
                    val previous = db.meta().get(AppDatabase.META_BUSINESS_ID)
                    if (previous != null && previous != owner) {
                        val unsent = UnsentChanges.of(db.outbox().all())
                        if (!unsent.isEmpty && !discardOthersChanges) return@withContext unsent
                        db.clearAllTables()
                        clearPdfs()
                    }
                    db.meta().put(LocalMeta(AppDatabase.META_BUSINESS_ID, owner))
                    null
                }
                if (blocked != null) {
                    // The new session is not kept; the other person's changes stay on the phone untouched.
                    ApiResult.Failure(ApiError(409, UNSENT_CHANGES, blocked.otherUserMessage()))
                } else {
                    stepUp.clear()
                    session.save(result.value)
                    ApiResult.Ok(Unit)
                }
            }
        }

    suspend fun logout() {
        session.refreshToken()?.let { token -> apiCallUnit { api.logout(LogoutRequest(token)) } }
        stepUp.clear()
        syncScheduler.stop()
        withContext(Dispatchers.IO) { database.get().clearAllTables(); clearPdfs() }
        session.clear()
    }

    suspend fun changePassword(current: String, new: String): ApiResult<Unit> =
        when (val r = apiCall { api.changePassword(ChangePasswordRequest(current, new)) }) {
            is ApiResult.Failure -> r
            is ApiResult.Ok -> {
                session.save(r.value)
                ApiResult.Ok(Unit)
            }
        }

    suspend fun reauthenticate(password: String): ApiResult<Unit> =
        when (val r = apiCall { api.reauth(ReauthRequest(password)) }) {
            is ApiResult.Failure -> r
            is ApiResult.Ok -> {
                stepUp.store(r.value.stepUpToken, r.value.expiresAt)
                ApiResult.Ok(Unit)
            }
        }

    suspend fun refreshProfile(): ApiResult<Unit> = when (val r = apiCall { api.me() }) {
        is ApiResult.Failure -> r
        is ApiResult.Ok -> {
            session.updateUser(r.value)
            ApiResult.Ok(Unit)
        }
    }

    suspend fun forgotPassword(username: String): ApiResult<Unit> =
        apiCallUnit { authApi.forgotPassword(ForgotPasswordRequest(username.trim())) }

    suspend fun resetPassword(code: String, newPassword: String): ApiResult<Unit> =
        apiCallUnit { authApi.resetPassword(ResetPasswordRequest(code.trim(), newPassword)) }
}
