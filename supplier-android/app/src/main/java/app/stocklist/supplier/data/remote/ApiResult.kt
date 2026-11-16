package app.stocklist.supplier.data.remote

import app.stocklist.supplier.core.i18n.tr
import app.stocklist.supplier.core.i18n.trKey
import app.stocklist.supplier.core.i18n.trMessage
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/** Result of a server call. Failure messages are written for suppliers, not developers, in English and Hindi. */
sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>
    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

data class ApiError(val status: Int, val code: String, val message: String) {
    val isNetwork: Boolean get() = code == NETWORK

    companion object {
        const val NETWORK = "NETWORK"
        val network: ApiError get() = ApiError(0, NETWORK, tr("No connection to the server. Check the internet and try again."))
    }
}

/**
 * The app's own text for every error code the supplier portal can send (SupplierPortalError on the server, plus the
 * general ones). Shown by code, so the message is always in the app's languages. SupplierHindiCatalogueTest checks
 * that every code the server defines is listed here and has Hindi.
 */
object ServerErrors {
    val texts: Map<String, String> = mapOf(
        "NAME_REQUIRED" to trKey("Enter the business name"),
        "CONTACT_REQUIRED" to trKey("Enter the contact person's name"),
        "ADDRESS_REQUIRED" to trKey("Enter the address, city and state"),
        "INVALID_MOBILE" to trKey("Enter a 10-digit mobile number"),
        "INVALID_EMAIL" to trKey("Enter a valid email address"),
        "INVALID_PIN_CODE" to trKey("Enter a 6-digit PIN code"),
        "INVALID_GSTIN" to trKey("Enter a valid 15-character GSTIN or leave it empty"),
        "TEXT_TOO_LONG" to trKey("Some text is too long"),
        "WEAK_PASSWORD" to trKey("Password must have 8 to 128 characters and must not be a common password"),
        "SAME_PASSWORD" to trKey("The new password must be different from the current one"),
        "DEVICE_REQUIRED" to trKey("Device information is missing"),
        "EMAIL_NOT_VERIFIED" to trKey("Verify your email first. Open the link we emailed you, or ask for a new one"),
        "INVALID_CREDENTIALS" to trKey("Wrong email or password. After 5 wrong attempts the account is locked for 15 minutes"),
        "WRONG_CURRENT_PASSWORD" to trKey("The current password is wrong"),
        "INVALID_REFRESH_TOKEN" to trKey("Please sign in again"),
        "ACCOUNT_INACTIVE" to trKey("This account is not active"),
        "TOO_MANY_ATTEMPTS" to trKey("Too many attempts. Please wait a minute and try again"),
        "LINK_INVALID" to trKey("This link is not valid. Please ask for a new one"),
        "LINK_EXPIRED" to trKey("This link has expired. Please ask for a new one"),
        "LINK_USED" to trKey("This link has already been used or replaced by a newer one"),
        "DETAILS_LOCKED" to trKey("Business details cannot be changed after approval"),
        "STALE_VERSION" to trKey("These details were changed elsewhere. Reload and try again"),
        "SUPPLIER_NOT_APPROVED" to trKey("Your business is not approved yet"),
        "SUPPLIER_SUSPENDED" to trKey("Your business account is suspended"),
        // General answers of the server
        "UNAUTHENTICATED" to trKey("Please sign in again"),
        "PERMISSION_DENIED" to trKey("You do not have permission for this action"),
        "VALIDATION_FAILED" to trKey("Some fields are invalid"),
        "BAD_REQUEST" to trKey("The request could not be read. Please update the app"),
        "CONFLICT" to trKey("This conflicts with existing data"),
        "NOT_FOUND" to trKey("Not found"),
        "INTERNAL_ERROR" to trKey("The server had a problem. Please try again"),
    )

    fun message(code: String?, status: Int): String {
        texts[code]?.let { return trMessage(it) }
        return when (status) {
            401 -> tr("Please sign in again")
            403 -> tr("You do not have permission for this action")
            404 -> tr("Not found")
            409 -> tr("This conflicts with existing data")
            429 -> tr("Too many attempts. Please wait a minute and try again")
            in 500..599 -> tr("The server had a problem. Please try again")
            else -> tr("Something went wrong. Please try again")
        }
    }
}

private val errorJson = Json { ignoreUnknownKeys = true }

suspend fun <T> apiCall(block: suspend () -> T): ApiResult<T> = try {
    ApiResult.Ok(block())
} catch (e: HttpException) {
    ApiResult.Failure(e.response().toApiError(e.code()))
} catch (e: IOException) {
    ApiResult.Failure(ApiError.network)
}

/** For endpoints that return no body (202/204). */
suspend fun apiCallUnit(block: suspend () -> Response<Unit>): ApiResult<Unit> = try {
    val res = block()
    if (res.isSuccessful) ApiResult.Ok(Unit) else ApiResult.Failure(res.toApiError(res.code()))
} catch (e: IOException) {
    ApiResult.Failure(ApiError.network)
}

fun Response<*>?.toApiError(status: Int): ApiError {
    val body = try {
        this?.errorBody()?.string()
    } catch (e: IOException) {
        null
    }
    val code = try {
        body?.let { errorJson.decodeFromString(ProblemDto.serializer(), it) }?.code
    } catch (e: Exception) {
        null
    }
    return ApiError(status, code ?: "HTTP_$status", ServerErrors.message(code, status))
}
