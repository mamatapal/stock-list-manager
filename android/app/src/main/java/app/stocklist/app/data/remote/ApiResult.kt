package app.stocklist.app.data.remote

import app.stocklist.app.core.i18n.tr
import app.stocklist.app.core.i18n.trMessage

import kotlinx.serialization.json.Json
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/** Result of a server call. Failure messages are written for shop staff, not developers. */
sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>
    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

data class ApiError(
    val status: Int,
    val code: String,
    val message: String,
    val fieldErrors: Map<String, String> = emptyMap(),
) {
    val isNetwork: Boolean get() = code == NETWORK
    val needsStepUp: Boolean get() = code == "STEP_UP_REQUIRED"

    companion object {
        const val NETWORK = "NETWORK"
        val network: ApiError get() = ApiError(0, NETWORK, tr("No connection to the server. Check the internet and try again."))
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

/** For endpoints that return no body (204/202). */
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
    val problem = try {
        body?.let { errorJson.decodeFromString(ProblemDto.serializer(), it) }
    } catch (e: Exception) {
        null
    }
    val fallback = when (status) {
        401 -> tr("Please sign in again.")
        403 -> tr("You do not have permission for this action.")
        404 -> tr("Not found.")
        409 -> tr("This conflicts with existing data.")
        429 -> tr("Too many attempts. Please wait a minute.")
        in 500..599 -> tr("The server had a problem. Please try again.")
        else -> tr("Something went wrong.")
    }
    return ApiError(
        status = status,
        code = problem?.code ?: "HTTP_$status",
        // Server messages are English; the Hindi catalogue has the server's fixed messages.
        message = problem?.detail?.let { trMessage(it) } ?: fallback,
        fieldErrors = problem?.errors?.associate { it.field to trMessage(it.message) } ?: emptyMap(),
    )
}
