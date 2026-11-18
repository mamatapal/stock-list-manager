package app.stocklist.supplier.network

import app.stocklist.supplier.data.remote.AuthApi
import app.stocklist.supplier.data.remote.NetworkApi
import app.stocklist.supplier.data.remote.SupplierApi
import app.stocklist.supplier.di.AppModule
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * The app's real Retrofit interfaces and JSON settings (AppModule.json()), pointed at a test server: a MockWebServer
 * in unit tests, or the real backend in the end-to-end test. Only the token handling is simplified (a fixed bearer
 * token instead of the phone's encrypted session store).
 */
class TestApis(baseUrl: String, token: () -> String? = { null }) {
    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val t = token()
                    val req = if (t == null) chain.request() else
                        chain.request().newBuilder().header("Authorization", "Bearer $t").build()
                    chain.proceed(req)
                }
                .build(),
        )
        .addConverterFactory(AppModule.json().asConverterFactory("application/json".toMediaType()))
        .build()

    val network: NetworkApi = retrofit.create(NetworkApi::class.java)
    val supplier: SupplierApi = retrofit.create(SupplierApi::class.java)
    val auth: AuthApi = retrofit.create(AuthApi::class.java)
}
