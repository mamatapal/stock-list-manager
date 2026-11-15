package app.stocklist.app.di

import android.content.Context
import androidx.room.Room
import app.stocklist.app.BuildConfig
import app.stocklist.app.core.session.SessionStore
import app.stocklist.app.data.local.AppDatabase
import app.stocklist.app.data.remote.ApiService
import app.stocklist.app.data.remote.AuthApi
import app.stocklist.app.data.remote.AuthInterceptor
import app.stocklist.app.data.remote.TokenAuthenticator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
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
                // Headers are not logged, so tokens never reach logcat.
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

    @Provides
    @Singleton
    fun apiService(json: Json, session: SessionStore, authApi: dagger.Lazy<AuthApi>): ApiService {
        val client = baseClient()
            .addInterceptor(AuthInterceptor(session))
            .authenticator(TokenAuthenticator(session) { authApi.get() })
            .build()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
    }

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context, session: SessionStore): AppDatabase {
        System.loadLibrary("sqlcipher")
        val key = runBlocking { session.databaseKey() }
        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .openHelperFactory(SupportOpenHelperFactory(key))
            .build()
    }
}
