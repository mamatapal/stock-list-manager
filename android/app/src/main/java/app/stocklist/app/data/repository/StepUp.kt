package app.stocklist.app.data.repository

import app.stocklist.app.core.i18n.tr

import app.stocklist.app.core.session.StepUpCache
import app.stocklist.app.data.remote.ApiError
import app.stocklist.app.data.remote.ApiResult
import app.stocklist.app.data.remote.apiCall

/**
 * Runs a sensitive call with the cached step-up token. With no valid token it returns STEP_UP_REQUIRED
 * without calling the server, and the screen asks for the password and retries.
 */
suspend fun <T> withStepUp(cache: StepUpCache, block: suspend (String) -> T): ApiResult<T> {
    val token = cache.valid()
        ?: return ApiResult.Failure(ApiError(403, "STEP_UP_REQUIRED", tr("Please re-enter your password to continue")))
    val result = apiCall { block(token) }
    if (result is ApiResult.Failure && result.error.needsStepUp) cache.clear()
    return result
}
