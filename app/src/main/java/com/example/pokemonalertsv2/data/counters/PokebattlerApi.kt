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
    private const val CACHE_BYTES = 6L * 1024 * 1024

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
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val client = OkHttpClient.Builder()
            .cache(Cache(File(appContext.cacheDir, CACHE_DIR), CACHE_BYTES))
            .addInterceptor(UserAgentInterceptor())
            .addInterceptor(OfflineCacheInterceptor(appContext))
            .addInterceptor(RateLimitRetryInterceptor())
            .addInterceptor(logging)
            // The ranking endpoint is slow when busy; the 10s default read timeout fires
            // and surfaces as a misleading connection error.
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
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
     * Backs off on HTTP 429.
     *
     * Pokebattler rate-limits in earnest, so this honours `Retry-After` when present and
     * otherwise waits a widening interval. It gives up after [MAX_RETRIES] and lets the 429
     * surface, which the repository turns into a "busy, try again" state — never a tight
     * retry loop.
     */
    private class RateLimitRetryInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            var attempt = 0
            var response = chain.proceed(chain.request())
            while (response.code == HTTP_TOO_MANY_REQUESTS && attempt < MAX_RETRIES) {
                val requested = response.retryAfterMillis()
                // Waiting longer than the service asks for is pointless, and waiting longer
                // than the call timeout turns a rate limit into a misleading "check your
                // connection". If it wants more time than we have, surface the 429 now.
                if (requested != null && requested > MAX_WAIT_MILLIS) return response

                response.close()
                try {
                    Thread.sleep(requested ?: BACKOFF_MILLIS[attempt])
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return chain.proceed(chain.request())
                }
                attempt++
                response = chain.proceed(chain.request())
            }
            return response
        }

        private fun Response.retryAfterMillis(): Long? = header("Retry-After")
            ?.trim()
            ?.toLongOrNull()
            ?.coerceAtLeast(0)
            ?.let { it * 1000 }

        private companion object {
            const val HTTP_TOO_MANY_REQUESTS = 429
            const val MAX_RETRIES = 2

            /** Total backoff must stay well inside the 45s call timeout. */
            const val MAX_WAIT_MILLIS = 5_000L
            val BACKOFF_MILLIS = longArrayOf(1_000L, 4_000L)
        }
    }
}
