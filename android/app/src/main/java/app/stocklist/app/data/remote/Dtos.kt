package app.stocklist.app.data.remote

import kotlinx.serialization.Serializable

// Requests -------------------------------------------------------------------------------------

@Serializable
data class RegisterBusinessRequest(
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
    val gstin: String?,
    val deviceId: String,
    val deviceName: String?,
    val appVersion: String?,
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
    val deviceId: String,
    val deviceName: String?,
    val appVersion: String?,
    /** "OWNER" or "STAFF": the owner and staff sign-in screens refuse the other kind of account. */
    val expectedRole: String?,
)

@Serializable data class RefreshRequest(val refreshToken: String)
@Serializable data class LogoutRequest(val refreshToken: String)
@Serializable data class ReauthRequest(val password: String)
@Serializable data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)
@Serializable data class ForgotPasswordRequest(val username: String)
@Serializable data class ResetPasswordRequest(val token: String, val newPassword: String)

@Serializable
data class UpdateBusinessRequest(
    val name: String,
    val address: String,
    val city: String,
    val state: String,
    val pinCode: String,
    val phone: String,
    val email: String,
    val gstin: String?,
)

@Serializable
data class CreateStaffRequest(
    val fullName: String,
    val username: String,
    val mobile: String?,
    val permissions: List<String>?,
)

@Serializable data class UpdateStaffRequest(val fullName: String, val mobile: String?)
@Serializable data class SetPermissionsRequest(val permissions: List<String>)

// Responses ------------------------------------------------------------------------------------

@Serializable
data class MeDto(
    val userId: String,
    val username: String,
    val fullName: String,
    val role: String,
    val businessId: String,
    val businessName: String,
    val mustChangePassword: Boolean,
    val permissions: List<String>,
) {
    val isOwner: Boolean get() = role == "OWNER"
}

@Serializable
data class SessionDto(
    val accessToken: String,
    val accessTokenExpiresAt: String,
    val refreshToken: String,
    val refreshTokenExpiresAt: String,
    val user: MeDto,
)

@Serializable data class StepUpDto(val stepUpToken: String, val expiresAt: String)

@Serializable
data class BusinessDto(
    val id: String,
    val name: String,
    val address: String,
    val city: String,
    val state: String,
    val pinCode: String,
    val phone: String,
    val email: String,
    val gstin: String? = null,
    val hasLogo: Boolean = false,
    val version: Long = 0,
    val updatedAt: String? = null,
)

@Serializable
data class StaffDto(
    val id: String,
    val username: String,
    val fullName: String,
    val mobile: String? = null,
    val status: String,
    val mustChangePassword: Boolean,
    val createdAt: String? = null,
    val lastLoginAt: String? = null,
) {
    val isActive: Boolean get() = status == "ACTIVE"
}

@Serializable
data class CreateStaffResponse(val staff: StaffDto, val permissions: List<String>, val temporaryPassword: String)

@Serializable data class PermissionsDto(val userId: String, val permissions: List<String>)
@Serializable data class TemporaryPasswordDto(val temporaryPassword: String)

@Serializable
data class SessionInfoDto(
    val familyId: String,
    val deviceId: String,
    val deviceName: String? = null,
    val signedInAt: String? = null,
    val expiresAt: String? = null,
)

@Serializable
data class ActivityEntryDto(
    val id: Long,
    val userId: String? = null,
    val username: String? = null,
    val action: String,
    val recordType: String? = null,
    val recordId: String? = null,
    val details: String? = null,
    val deviceId: String? = null,
    val createdAt: String,
)

@Serializable
data class ActivityPageDto(val items: List<ActivityEntryDto>, val page: Int, val size: Int, val total: Long)

@Serializable data class FieldErrorDto(val field: String, val message: String)

@Serializable
data class ProblemDto(
    val status: Int? = null,
    val code: String? = null,
    val detail: String? = null,
    val errors: List<FieldErrorDto>? = null,
)
