package com.example.pokemonalertsv2.account

import com.example.pokemonalertsv2.sync.appKey
import kotlinx.serialization.Serializable
import okhttp3.Interceptor
import java.io.IOException
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/** The backend's account routes (DEVICE_SYNC.md, "Accounts", in AlsbachScanner). */
interface AccountService {
    @GET("api/account")
    suspend fun account(
        @Header("X-Sync-Device") device: String,
        @Header("X-App-Key") appKey: String? = appKey()
    ): Response<AccountDto>

    @POST("api/account/devices/self")
    suspend fun registerSelf(
        @Header("X-Sync-Device") device: String,
        @Body body: DeviceRegistration,
        @Header("X-App-Key") appKey: String? = appKey()
    ): Response<ResponseBody>

    /** Attach-or-get: links [body]'s group when the account has none, else returns the account's. */
    @PUT("api/account/sync")
    suspend fun attach(
        @Header("X-Sync-Device") device: String,
        @Body body: SecretDto,
        @Header("X-App-Key") appKey: String? = appKey()
    ): Response<AttachDto>

    @DELETE("api/account/devices/{deviceId}")
    suspend fun removeDevice(
        @Path("deviceId") deviceId: String,
        @Header("X-Sync-Device") device: String,
        @Header("X-App-Key") appKey: String? = appKey()
    ): Response<ResponseBody>

    @DELETE("api/account")
    suspend fun delete(
        @Header("X-Sync-Device") device: String,
        @Header("X-App-Key") appKey: String? = appKey()
    ): Response<ResponseBody>
}

@Serializable
data class AccountDto(
    val uid: String,
    val email: String? = null,
    val sync: SecretDto? = null,
    val devices: List<AccountDeviceDto> = emptyList()
)

@Serializable
data class AccountDeviceDto(
    val deviceId: String,
    val name: String? = null,
    val model: String? = null,
    val appVersion: Long? = null,
    val lastSeenAt: String? = null,
    val self: Boolean = false
)

@Serializable
data class SecretDto(val secret: String)

@Serializable
data class AttachDto(val secret: String, val attached: Boolean = false)

@Serializable
data class DeviceRegistration(val name: String, val model: String, val appVersion: Long)

/** The server refused an account request; [code] is its error code (e.g. DEVICE_REVOKED). */
class AccountApiException(val status: Int, val code: String?) : Exception(code ?: "HTTP $status")

private val CODE = Regex("\"code\"\\s*:\\s*\"([A-Z_]+)\"")

internal fun Response<*>.failure(): AccountApiException {
    val body = runCatching { errorBody()?.string() }.getOrNull().orEmpty()
    return AccountApiException(code(), CODE.find(body)?.groupValues?.get(1))
}

internal fun <T> Response<T>.bodyOrThrow(): T = body()?.takeIf { isSuccessful } ?: throw failure()

internal fun Response<*>.orThrow() {
    if (!isSuccessful) throw failure()
}

/**
 * Adds the signed-in account's Firebase ID token to the account and sync requests. Signed
 * out, it adds nothing: groups not linked to an account sync without it. Signed in but
 * without a token (offline, Firebase unreachable), the request fails as a network error and
 * is retried, instead of reaching the server as "signed out".
 */
object AccountAuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        if (request.header("Authorization") != null || !AccountRepository.isSignedIn()) return chain.proceed(request)
        val token = AccountRepository.idTokenBlocking() ?: throw IOException("No ID token for the signed-in account")
        return chain.proceed(request.newBuilder().header("Authorization", "Bearer $token").build())
    }
}
