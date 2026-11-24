package app.stocklist.supplier.data.remote

import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

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

/**
 * Supplier Network calls of the signed-in supplier (only for APPROVED businesses; the server answers
 * SUPPLIER_NOT_APPROVED / SUPPLIER_SUSPENDED otherwise). Online only: nothing is queued or stored on the phone.
 */
interface NetworkApi {
    @GET("api/v1/supplier-portal/directory-listing")
    suspend fun listing(): ListingDto

    @PUT("api/v1/supplier-portal/directory-listing")
    suspend fun setListing(@Body body: ListingRequest): ListingDto

    @GET("api/v1/supplier-portal/connections")
    suspend fun connections(@Query("status") status: String? = null): List<ConnectionDto>

    @GET("api/v1/supplier-portal/connections/{id}")
    suspend fun connection(@Path("id") id: String): ConnectionDto

    @POST("api/v1/supplier-portal/connections/{id}/accept")
    suspend fun acceptConnection(@Path("id") id: String): ConnectionDto

    @POST("api/v1/supplier-portal/connections/{id}/decline")
    suspend fun declineConnection(@Path("id") id: String, @Body body: ReasonRequest): ConnectionDto

    @POST("api/v1/supplier-portal/connections/{id}/end")
    suspend fun endConnection(@Path("id") id: String, @Body body: ReasonRequest): ConnectionDto

    @GET("api/v1/supplier-portal/products")
    suspend fun products(): List<ProductDto>

    @POST("api/v1/supplier-portal/products")
    suspend fun createProduct(@Body body: ProductRequest): ProductDto

    @PUT("api/v1/supplier-portal/products/{id}")
    suspend fun updateProduct(@Path("id") id: String, @Body body: ProductRequest): ProductDto

    @POST("api/v1/supplier-portal/products/{id}/deactivate")
    suspend fun deactivateProduct(@Path("id") id: String): ProductDto

    @POST("api/v1/supplier-portal/products/{id}/activate")
    suspend fun activateProduct(@Path("id") id: String): ProductDto

    @GET("api/v1/supplier-portal/orders")
    suspend fun orders(@Query("status") status: String? = null): List<OrderDto>

    @GET("api/v1/supplier-portal/orders/{id}")
    suspend fun order(@Path("id") id: String): OrderDto

    @POST("api/v1/supplier-portal/orders/{id}/accept")
    suspend fun acceptOrder(@Path("id") id: String): OrderDto

    @POST("api/v1/supplier-portal/orders/{id}/reject")
    suspend fun rejectOrder(@Path("id") id: String, @Body body: ReasonRequest): OrderDto

    @POST("api/v1/supplier-portal/orders/{id}/dispatch")
    suspend fun dispatchOrder(@Path("id") id: String, @Body body: ReasonRequest): OrderDto
}
