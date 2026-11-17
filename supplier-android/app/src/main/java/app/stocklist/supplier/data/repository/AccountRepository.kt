package app.stocklist.supplier.data.repository

import android.os.Build
import app.stocklist.supplier.core.session.SessionStore
import app.stocklist.supplier.data.remote.ApiResult
import app.stocklist.supplier.data.remote.AuthApi
import app.stocklist.supplier.data.remote.ChangePasswordRequest
import app.stocklist.supplier.data.remote.EmailRequest
import app.stocklist.supplier.data.remote.LoginRequest
import app.stocklist.supplier.data.remote.MeDto
import app.stocklist.supplier.data.remote.ProfileRequest
import app.stocklist.supplier.data.remote.RefreshRequest
import app.stocklist.supplier.data.remote.RegisterRequest
import app.stocklist.supplier.data.remote.SupplierApi
import app.stocklist.supplier.data.remote.apiCall
import app.stocklist.supplier.data.remote.apiCallUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Registration, sign-in and the business account. Everything needs the internet; nothing is kept offline. */
@Singleton
class AccountRepository @Inject constructor(
    private val auth: AuthApi,
    private val api: SupplierApi,
    private val session: SessionStore,
) {
    suspend fun register(request: RegisterRequest): ApiResult<Unit> = apiCallUnit { auth.register(request) }

    suspend fun resendVerification(email: String): ApiResult<Unit> =
        apiCallUnit { auth.resendVerification(EmailRequest(email.trim())) }

    suspend fun forgotPassword(email: String): ApiResult<Unit> =
        apiCallUnit { auth.forgotPassword(EmailRequest(email.trim())) }

    suspend fun login(email: String, password: String): ApiResult<Unit> {
        val device = session.deviceId()
        val r = apiCall { auth.login(LoginRequest(email.trim(), password, device, Build.MODEL)) }
        return when (r) {
            is ApiResult.Ok -> { session.save(r.value); ApiResult.Ok(Unit) }
            is ApiResult.Failure -> r
        }
    }

    /** Fresh account details from the server (review status may have changed). */
    suspend fun refreshMe(): ApiResult<MeDto> {
        val r = apiCall { api.me() }
        if (r is ApiResult.Ok) session.updateMe(r.value)
        return r
    }

    suspend fun updateProfile(version: Long, request: ProfileRequest): ApiResult<MeDto> {
        val r = apiCall { api.updateProfile(version, request) }
        if (r is ApiResult.Ok) session.updateMe(r.value)
        return r
    }

    suspend fun changePassword(current: String, new: String): ApiResult<Unit> {
        val r = apiCall { api.changePassword(ChangePasswordRequest(current, new)) }
        return when (r) {
            is ApiResult.Ok -> { session.save(r.value); ApiResult.Ok(Unit) }
            is ApiResult.Failure -> r
        }
    }

    /** Signs out on the server when possible; this phone forgets the session either way. */
    suspend fun logout() {
        session.refreshToken()?.let { token -> apiCallUnit { api.logout(RefreshRequest(token)) } }
        session.clear()
    }
}
