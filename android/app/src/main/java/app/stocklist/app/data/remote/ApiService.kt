package app.stocklist.app.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

const val STEP_UP_HEADER = "X-Step-Up-Token"

/** Calls made with the signed-in user's access token (refreshed automatically on 401). */
interface ApiService {
    @POST("api/v1/auth/logout")
    suspend fun logout(@Body body: LogoutRequest): Response<Unit>

    @POST("api/v1/auth/reauth")
    suspend fun reauth(@Body body: ReauthRequest): StepUpDto

    @POST("api/v1/auth/password/change")
    suspend fun changePassword(@Body body: ChangePasswordRequest): SessionDto

    @GET("api/v1/auth/me")
    suspend fun me(): MeDto

    @GET("api/v1/reports/purchases")
    suspend fun reportPurchases(@Query("from") from: String, @Query("to") to: String): PurchaseReportDto

    @GET("api/v1/reports/supplier-totals")
    suspend fun reportSupplierTotals(@Query("from") from: String, @Query("to") to: String): SupplierTotalsDto

    @GET("api/v1/reports/item-prices")
    suspend fun reportItemPrices(@Query("from") from: String, @Query("to") to: String): ItemPricesDto

    @GET("api/v1/reports/expected-profit")
    suspend fun reportExpectedProfit(@Query("from") from: String, @Query("to") to: String): ExpectedProfitReportDto

    @GET("api/v1/business")
    suspend fun business(): BusinessDto

    @PUT("api/v1/business")
    suspend fun updateBusiness(@Header(STEP_UP_HEADER) stepUp: String, @Body body: UpdateBusinessRequest): BusinessDto

    @GET("api/v1/purchases/{id}/revisions")
    suspend fun purchaseRevisions(@retrofit2.http.Path("id") id: String): List<RevisionDto>

    @POST("api/v1/sync/push")
    suspend fun syncPush(@Body body: PushRequest): PushResponse

    @GET("api/v1/sync/pull")
    suspend fun syncPull(@retrofit2.http.Query("since") since: Long, @retrofit2.http.Query("limit") limit: Int): PullResponse

    @GET("api/v1/activity")
    suspend fun activity(@retrofit2.http.Query("page") page: Int, @retrofit2.http.Query("size") size: Int): ActivityPageDto

    @GET("api/v1/staff")
    suspend fun staff(): List<StaffDto>

    @GET("api/v1/staff/{id}")
    suspend fun staffMember(@Path("id") id: String): StaffDto

    @POST("api/v1/staff")
    suspend fun createStaff(@Header(STEP_UP_HEADER) stepUp: String, @Body body: CreateStaffRequest): CreateStaffResponse

    @PUT("api/v1/staff/{id}")
    suspend fun updateStaff(
        @Header(STEP_UP_HEADER) stepUp: String,
        @Path("id") id: String,
        @Body body: UpdateStaffRequest,
    ): StaffDto

    @POST("api/v1/staff/{id}/activate")
    suspend fun activateStaff(@Header(STEP_UP_HEADER) stepUp: String, @Path("id") id: String): StaffDto

    @POST("api/v1/staff/{id}/deactivate")
    suspend fun deactivateStaff(@Header(STEP_UP_HEADER) stepUp: String, @Path("id") id: String): StaffDto

    @POST("api/v1/staff/{id}/reset-password")
    suspend fun resetStaffPassword(@Header(STEP_UP_HEADER) stepUp: String, @Path("id") id: String): TemporaryPasswordDto

    @GET("api/v1/staff/{id}/permissions")
    suspend fun staffPermissions(@Path("id") id: String): PermissionsDto

    @PUT("api/v1/staff/{id}/permissions")
    suspend fun setStaffPermissions(
        @Header(STEP_UP_HEADER) stepUp: String,
        @Path("id") id: String,
        @Body body: SetPermissionsRequest,
    ): PermissionsDto

    @GET("api/v1/staff/{id}/sessions")
    suspend fun staffSessions(@Path("id") id: String): List<SessionInfoDto>

    @DELETE("api/v1/staff/{id}/sessions")
    suspend fun revokeStaffSessions(@Header(STEP_UP_HEADER) stepUp: String, @Path("id") id: String): Response<Unit>
}

/** Calls that need no access token. Uses a client without the refresh authenticator. */
interface AuthApi {
    @POST("api/v1/auth/register-business")
    suspend fun register(@Body body: RegisterBusinessRequest): SessionDto

    @POST("api/v1/auth/login")
    suspend fun login(@Body body: LoginRequest): SessionDto

    @POST("api/v1/auth/refresh")
    fun refreshBlocking(@Body body: RefreshRequest): retrofit2.Call<SessionDto>

    @POST("api/v1/auth/password/forgot")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequest): Response<Unit>

    @POST("api/v1/auth/password/reset")
    suspend fun resetPassword(@Body body: ResetPasswordRequest): Response<Unit>
}
