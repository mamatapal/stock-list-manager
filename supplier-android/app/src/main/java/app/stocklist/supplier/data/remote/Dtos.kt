package app.stocklist.supplier.data.remote

import kotlinx.serialization.Serializable

// Requests and answers of the supplier portal API (/api/v1/supplier-portal), see docs/API.md.

@Serializable
data class RegisterRequest(
    val legalName: String,
    val tradeName: String?,
    val contactPerson: String,
    val mobile: String,
    val email: String,
    val password: String,
    val address: String,
    val city: String,
    val state: String,
    val pinCode: String,
    val gstin: String?,
)

@Serializable
data class ProfileRequest(
    val legalName: String,
    val tradeName: String?,
    val contactPerson: String,
    val mobile: String,
    val address: String,
    val city: String,
    val state: String,
    val pinCode: String,
    val gstin: String?,
)

@Serializable
data class EmailRequest(val email: String)

@Serializable
data class LoginRequest(val email: String, val password: String, val deviceId: String, val deviceName: String?)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

@Serializable
data class UserDto(
    val id: String,
    val email: String,
    val fullName: String,
    val mobile: String,
    val role: String,
    val emailVerified: Boolean,
)

@Serializable
data class OrgDto(
    val id: String,
    val legalName: String,
    val tradeName: String? = null,
    val contactPerson: String,
    val mobile: String,
    val email: String,
    val address: String,
    val city: String,
    val state: String,
    val pinCode: String,
    val gstin: String? = null,
    val status: String,
    val statusReason: String? = null,
    val submittedAt: String? = null,
    val reviewedAt: String? = null,
    val canEditDetails: Boolean = false,
    val version: Long = 0,
)

@Serializable
data class MeDto(val user: UserDto, val org: OrgDto)

@Serializable
data class SessionDto(
    val accessToken: String,
    val accessTokenExpiresAt: String,
    val refreshToken: String,
    val refreshTokenExpiresAt: String,
    val me: MeDto,
)

@Serializable
data class ProblemDto(
    val status: Int? = null,
    val code: String? = null,
    val detail: String? = null,
)

/** Review states of a supplier business (same names as the server). */
object OrgStatus {
    const val PENDING_EMAIL_VERIFICATION = "PENDING_EMAIL_VERIFICATION"
    const val PENDING_APPROVAL = "PENDING_APPROVAL"
    const val APPROVED = "APPROVED"
    const val REJECTED = "REJECTED"
    const val SUSPENDED = "SUSPENDED"
}
