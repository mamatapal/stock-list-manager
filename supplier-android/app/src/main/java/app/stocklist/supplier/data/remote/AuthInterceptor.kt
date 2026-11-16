package app.stocklist.supplier.data.remote

import app.stocklist.supplier.BuildConfig
import app.stocklist.supplier.core.session.SessionStore
import okhttp3.Interceptor
import okhttp3.Response

/** Adds the access token and app version to every signed-in call. */
class AuthInterceptor(private val session: SessionStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder().header("X-App-Version", BuildConfig.VERSION_NAME)
        session.accessToken?.let { builder.header("Authorization", "Bearer $it") }
        return chain.proceed(builder.build())
    }
}
