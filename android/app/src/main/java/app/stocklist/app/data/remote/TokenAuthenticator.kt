package app.stocklist.app.data.remote

import app.stocklist.app.core.i18n.tr

import app.stocklist.app.core.session.SessionStore
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * On 401, swaps the refresh token for a new pair (rotation) exactly once, even when several calls fail
 * at the same moment, then retries. If the refresh is refused the user is signed out.
 */
class TokenAuthenticator(
    private val session: SessionStore,
    private val authApi: () -> AuthApi,
) : Authenticator {
    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null
        val sentWith = response.request.header("Authorization")?.removePrefix("Bearer ")
        synchronized(lock) {
            val current = session.accessToken
            if (current != null && current != sentWith) {
                // Another call already refreshed while we waited.
                return response.request.newBuilder().header("Authorization", "Bearer $current").build()
            }
            val refresh = session.refreshTokenBlocking() ?: return null
            val result = try {
                authApi().refreshBlocking(RefreshRequest(refresh)).execute()
            } catch (e: java.io.IOException) {
                return null // offline: keep the session, the call simply fails for now
            }
            if (!result.isSuccessful) {
                val code = result.toApiError(result.code())
                session.clearBlocking(
                    if (code.code == "ACCOUNT_INACTIVE") code.message else tr("Your session has ended. Please sign in again."),
                )
                return null
            }
            val body = result.body() ?: return null
            session.saveBlocking(body)
            return response.request.newBuilder().header("Authorization", "Bearer ${body.accessToken}").build()
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
