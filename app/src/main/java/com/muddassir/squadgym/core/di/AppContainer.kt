package com.muddassir.squadgym.core.di

import android.content.Context
import androidx.room.Room
import com.muddassir.squadgym.BuildConfig
import com.muddassir.squadgym.core.error.ApiResult
import com.muddassir.squadgym.data.local.CacheStore
import com.muddassir.squadgym.data.local.SecureTokenStore
import com.muddassir.squadgym.data.local.SettingsStore
import com.muddassir.squadgym.data.local.SquadGymDatabase
import com.muddassir.squadgym.data.remote.ApiCaller
import com.muddassir.squadgym.data.remote.api.AdminApi
import com.muddassir.squadgym.data.remote.api.AttendanceApi
import com.muddassir.squadgym.data.remote.api.AuthApi
import com.muddassir.squadgym.data.remote.api.DashboardApi
import com.muddassir.squadgym.data.remote.api.MeApi
import com.muddassir.squadgym.data.remote.api.MemberApi
import com.muddassir.squadgym.data.remote.api.MembershipApi
import com.muddassir.squadgym.data.remote.interceptor.AuthInterceptor
import com.muddassir.squadgym.data.remote.interceptor.TokenAuthenticator
import com.muddassir.squadgym.data.remote.dto.ApiEnvelope
import com.muddassir.squadgym.data.repository.AdminRepository
import com.muddassir.squadgym.data.repository.AttendanceRepository
import com.muddassir.squadgym.data.repository.AuthRepository
import com.muddassir.squadgym.data.repository.DashboardRepository
import com.muddassir.squadgym.data.repository.LeaderboardRepository
import com.muddassir.squadgym.data.repository.MeRepository
import com.muddassir.squadgym.data.repository.MemberRepository
import com.muddassir.squadgym.data.repository.MembershipRepository
import com.muddassir.squadgym.data.session.SessionManager
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Manual dependency container.
 *
 * A DI framework would add build complexity without adding clarity at this size;
 * every dependency is explicit, lazy and testable.
 */
class AppContainer(appContext: Context) {

    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
        coerceInputValues = true
    }

    val tokenStore = SecureTokenStore(appContext)
    val settingsStore = SettingsStore(appContext)
    val sessionManager = SessionManager(tokenStore, json)

    /** Set from an `squadgym://invite/<token>` deep link, consumed by the auth flow. */
    val pendingInviteToken = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    val database: SquadGymDatabase by lazy {
        Room.databaseBuilder(appContext, SquadGymDatabase::class.java, "squadgym-cache.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    val cacheStore: CacheStore by lazy { CacheStore(database, json) }
    private val apiCaller: ApiCaller by lazy { ApiCaller(json, sessionManager) }

    private val baseUrl: String = BuildConfig.API_BASE_URL.let { if (it.endsWith("/")) it else "$it/" }

    private val logging = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        redactHeader("Authorization")
    }

    private val publicClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    private val refreshRetrofit: Retrofit by lazy { buildRetrofit(publicClient) }
    private val refreshApi: AuthApi by lazy { refreshRetrofit.create(AuthApi::class.java) }

    /** Client used for every request and by Coil for profile images. */
    val authorizedClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenStore))
            .addInterceptor(logging)
            .authenticator(
                TokenAuthenticator(
                    tokenStore = tokenStore,
                    refreshApiProvider = { refreshApi },
                    onSessionRefreshed = { sessionManager.onTokensRefreshed(it) },
                    onRefreshFailed = { sessionManager.forceSignOut() },
                ),
            )
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    private val retrofit: Retrofit by lazy { buildRetrofit(authorizedClient) }

    private fun buildRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val authApi: AuthApi by lazy { retrofit.create(AuthApi::class.java) }
    val dashboardApi: DashboardApi by lazy { retrofit.create(DashboardApi::class.java) }
    val memberApi: MemberApi by lazy { retrofit.create(MemberApi::class.java) }
    val attendanceApi: AttendanceApi by lazy { retrofit.create(AttendanceApi::class.java) }
    val membershipApi: MembershipApi by lazy { retrofit.create(MembershipApi::class.java) }
    val meApi: MeApi by lazy { retrofit.create(MeApi::class.java) }
    val adminApi: AdminApi by lazy { retrofit.create(AdminApi::class.java) }

    val authRepository: AuthRepository by lazy { AuthRepository(authApi, apiCaller, sessionManager, tokenStore, cacheStore) }
    val dashboardRepository: DashboardRepository by lazy { DashboardRepository(dashboardApi, cacheStore, apiCaller) }
    val memberRepository: MemberRepository by lazy { MemberRepository(memberApi, cacheStore, apiCaller) }
    val attendanceRepository: AttendanceRepository by lazy { AttendanceRepository(attendanceApi, cacheStore, apiCaller) }
    val membershipRepository: MembershipRepository by lazy {
        MembershipRepository(memberApi, membershipApi, dashboardApi, cacheStore, apiCaller)
    }
    val leaderboardRepository: LeaderboardRepository by lazy { LeaderboardRepository(dashboardApi, cacheStore, apiCaller) }
    val meRepository: MeRepository by lazy { MeRepository(meApi, cacheStore, apiCaller) }
    val adminRepository: AdminRepository by lazy { AdminRepository(adminApi, cacheStore, apiCaller) }

    /** Exposed for the WorkManager sync and screens that need the raw envelope. */
    suspend fun <T> execute(block: suspend () -> ApiEnvelope<T>): ApiResult<T> = apiCaller.call(block)
}
