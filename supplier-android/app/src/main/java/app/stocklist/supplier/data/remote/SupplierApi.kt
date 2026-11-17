package app.stocklist.supplier.data.remote

import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT

/** Calls that need no sign-in (and the token refresh, which must not go through the authenticator itself). */
interface AuthApi {
    @POST("api/v1/supplier-portal/auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<Unit>

    @POST("api/v1/supplier-portal/auth/resend-verification")
    suspend fun resendVerification(@Body body: EmailRequest): Response<Unit>

    @POST("api/v1/supplier-portal/auth/login")
    suspend fun login(@Body body: LoginRequest): SessionDto

    @POST("api/v1/supplier-portal/auth/password/forgot")
    suspend fun forgotPassword(@Body body: EmailRequest): Response<Unit>

    @POST("api/v1/supplier-portal/auth/refresh")
    fun refreshBlocking(@Body body: RefreshRequest): Call<SessionDto>
}

/** Calls made as the signed-in supplier (access token added by AuthInterceptor). */
interface SupplierApi {
    @GET("api/v1/supplier-portal/me")
    suspend fun me(): MeDto

    @PUT("api/v1/supplier-portal/profile")
    suspend fun updateProfile(@Header("If-Match") version: Long, @Body body: ProfileRequest): MeDto

    @POST("api/v1/supplier-portal/auth/password/change")
    suspend fun changePassword(@Body body: ChangePasswordRequest): SessionDto

    @POST("api/v1/supplier-portal/auth/logout")
    suspend fun logout(@Body body: RefreshRequest): Response<Unit>
}
