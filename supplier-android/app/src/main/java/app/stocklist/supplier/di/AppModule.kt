package app.stocklist.supplier.di

import app.stocklist.supplier.BuildConfig
import app.stocklist.supplier.core.session.SessionStore
import app.stocklist.supplier.data.remote.AuthApi
import app.stocklist.supplier.data.remote.AuthInterceptor
import app.stocklist.supplier.data.remote.NetworkApi
import app.stocklist.supplier.data.remote.SupplierApi
import app.stocklist.supplier.data.remote.TokenAuthenticator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun baseClient(): OkHttpClient.Builder = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                // Headers and bodies are not logged, so tokens and passwords never reach logcat.
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            }
        }

    @Provides
    @Singleton
    @Named("plain")
    fun plainRetrofit(json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(baseClient().build())
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun authApi(@Named("plain") retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    /** Calls as the signed-in supplier: the access token is added, and refreshed once when it has expired. */
    @Provides
    @Singleton
    @Named("signedIn")
    fun signedInRetrofit(json: Json, session: SessionStore, authApi: dagger.Lazy<AuthApi>): Retrofit {
        val client = baseClient()
            .addInterceptor(AuthInterceptor(session))
            .authenticator(TokenAuthenticator(session) { authApi.get() })
            .build()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun supplierApi(@Named("signedIn") retrofit: Retrofit): SupplierApi = retrofit.create(SupplierApi::class.java)

    @Provides
    @Singleton
    fun networkApi(@Named("signedIn") retrofit: Retrofit): NetworkApi = retrofit.create(NetworkApi::class.java)
}
