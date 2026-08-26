package com.example.pokemonalertsv2.data.counters

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.pokemonalertsv2.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Client for `fight.pokebattler.com`.
 *
 * Separate from [com.example.pokemonalertsv2.data.PokemonAlertsApi] because it is a
 * different host with very different traffic characteristics: responses are large
 * (~330 KB gzipped), highly cacheable (the server sends `max-age=3600`), and the service
 * rate-limits. Hence its own disk cache and the retry policy below.
 */
object PokebattlerApi {

    private const val CACHE_DIR = "pokebattler_http"
    // A single counters response is ~330 KB gzipped and the personal ranking needs the
    // level-40 and level-50 responses to live alongside the generic list, so 6 MB used to
    // thrash.
    private const val CACHE_BYTES = 32L * 1024 * 1024

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Volatile
    private var instance: PokebattlerService? = null

    fun service(context: Context): PokebattlerService =
        instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

    private fun build(appContext: Context): PokebattlerService {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                // HEADERS, not BASIC, so it is visible whether the Pokébox request carried
                // its session token — an unauthenticated one hangs rather than 401ing, which
                // is indistinguishable from a slow one in the log otherwise.
                HttpLoggingInterceptor.Level.HEADERS
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            // The token is a bearer credential; never let it reach logcat.
            redactHeader("X-Authorization")
        }

        val client = OkHttpClient.Builder()
            .cache(Cache(File(appContext.cacheDir, CACHE_DIR), CACHE_BYTES))
            .addInterceptor(UserAgentInterceptor())
            .addInterceptor(OfflineCacheInterceptor(appContext))
            .addInterceptor(RateLimitPassthroughInterceptor())
            .addInterceptor(logging)
            // The ranking endpoint is slow when busy; the 10s default read timeout fires
            // and surfaces as a misleading connection error.
            .connectTimeout(20, TimeUnit.SECONDS)
            // Pokébox ranking is far slower than a by-level one: Pokebattler's gateway
            // itself gives up at 30 s, so a 30 s read timeout raced it and surfaced the
            // server's 504 as a misleading "check your connection".
            .readTimeout(45, TimeUnit.SECONDS)
            .callTimeout(75, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.POKEBATTLER_API_BASE_URL)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .client(client)
            .build()
            .create()
    }

    /** Identifies this app so Pokebattler can attribute (or block) the traffic. */
    private class UserAgentInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request().newBuilder()
                .header(
                    "User-Agent",
                    "PokemonAlertsV2/${BuildConfig.VERSION_NAME} (Android; raid counters)"
                )
                .build()
            return chain.proceed(request)
        }
    }

    /**
     * With no network, ask for a cached response rather than failing outright, so a raid
     * opened offline still shows its counters.
     */
    private class OfflineCacheInterceptor(private val context: Context) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            if (isOnline()) return chain.proceed(chain.request())
            val offline = chain.request().newBuilder()
                .cacheControl(
                    CacheControl.Builder()
                        .onlyIfCached()
                        .maxStale(7, TimeUnit.DAYS)
                        .build()
                )
                .build()
            return chain.proceed(offline)
        }

        private fun isOnline(): Boolean {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }

    /**
     * Surfaces HTTP 429 immediately, on purpose.
     *
     * This used to sleep and retry twice. On the counters endpoint a 429 does not mean
     * "you are going too fast" — it means Pokebattler has not precomputed that parameter
     * combination for that boss, and it always comes with `Retry-After: 5`. Waiting never
     * helps: five attempts spread over four minutes returned 429, 429, 504, 429, 429. The
     * retries cost ~20 s and buried the useful signal, so the repository's fallback to
     * [RaidCounterOptions.precomputedBaseline] now handles it instead — that request is
     * both fast and actually available.
     *
     * Kept as a named marker rather than deleted so the reason survives in one place.
     */
    private class RateLimitPassthroughInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
    }
}
