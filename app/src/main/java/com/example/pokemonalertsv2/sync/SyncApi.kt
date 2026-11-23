package com.example.pokemonalertsv2.sync

import com.example.pokemonalertsv2.BuildConfig
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/** The backend's device sync routes (DEVICE_SYNC.md in AlsbachScanner). */
interface SyncService {
    @GET("api/sync/{groupId}")
    suspend fun list(
        @Path("groupId") groupId: String,
        @Header("X-Sync-Auth") auth: String,
        @Header("X-Sync-Device") device: String?,
        @Header("X-App-Key") appKey: String? = appKey()
    ): Response<SyncCollectionList>

    @GET("api/sync/{groupId}/{collection}")
    suspend fun fetch(
        @Path("groupId") groupId: String,
        @Path("collection") collection: String,
        @Header("X-Sync-Auth") auth: String,
        @Header("If-None-Match") ifNoneMatch: String?,
        @Header("X-Sync-Device") device: String?,
        @Header("X-App-Key") appKey: String? = appKey()
    ): Response<ResponseBody>

    @PUT("api/sync/{groupId}/{collection}")
    suspend fun put(
        @Path("groupId") groupId: String,
        @Path("collection") collection: String,
        @Header("X-Sync-Auth") auth: String,
        @Header("If-Match") ifMatch: String,
        @Header("X-Sync-Device") device: String,
        @Body body: RequestBody,
        @Header("X-App-Key") appKey: String? = appKey()
    ): Response<ResponseBody>

    @DELETE("api/sync/{groupId}")
    suspend fun delete(
        @Path("groupId") groupId: String,
        @Header("X-Sync-Auth") auth: String,
        @Header("X-Sync-Device") device: String?,
        @Header("X-App-Key") appKey: String? = appKey()
    ): Response<ResponseBody>

    /** Relayed to the group's live streams, never stored (DEVICE_SYNC.md, "Presence"). */
    @POST("api/sync/{groupId}/presence")
    suspend fun presence(
        @Path("groupId") groupId: String,
        @Header("X-Sync-Auth") auth: String,
        @Header("X-Sync-Device") device: String,
        @Body body: RequestBody,
        @Header("X-App-Key") appKey: String? = appKey()
    ): Response<ResponseBody>
}

internal fun appKey(): String? = BuildConfig.APP_API_KEY.takeIf { it.isNotBlank() }

@Serializable
data class SyncCollectionList(val collections: List<SyncCollectionInfo> = emptyList())

@Serializable
data class SyncCollectionInfo(val name: String, val version: Long = 0, val updatedAt: String? = null, val bytes: Long = 0)

/** The server no longer knows the group: deleted from another device, or purged when idle. */
class SyncGroupGoneException : Exception("Sync group not found")

/** The server does not carry this collection yet (an older backend): skipped, not a failure. */
class SyncCollectionUnknownException(val collection: String) : Exception("Server does not know $collection")

/** Anything else the server refused or could not answer. */
class SyncTransportException(val status: Int, message: String) : Exception(message)

/**
 * The group is linked to an account and the server refused this device for it
 * (DEVICE_SYNC.md, "Accounts"): signed out ([SIGN_IN_REQUIRED], [TOKEN_INVALID]), signed in to
 * another account ([MISMATCH]) or removed from it ([DEVICE_REVOKED]).
 */
class SyncAccountException(val code: String) : Exception(code) {
    val revoked: Boolean get() = code == DEVICE_REVOKED

    companion object {
        const val SIGN_IN_REQUIRED = "ACCOUNT_SIGN_IN_REQUIRED"
        const val TOKEN_INVALID = "ACCOUNT_TOKEN_INVALID"
        const val MISMATCH = "ACCOUNT_MISMATCH"
        const val DEVICE_REVOKED = "DEVICE_REVOKED"
        private val CODES = setOf(SIGN_IN_REQUIRED, TOKEN_INVALID, MISMATCH, DEVICE_REVOKED)

        /** The account refusal in a 401 or 403 body, or null for any other answer. */
        fun fromResponse(status: Int, body: String?): SyncAccountException? {
            if (status != 401 && status != 403) return null
            val code = CODES.firstOrNull { body.orEmpty().contains("\"$it\"") } ?: return null
            return SyncAccountException(code)
        }
    }
}

sealed interface FetchResult {
    data object NotModified : FetchResult
    data object Empty : FetchResult
    class Blob(val version: Long, val bytes: ByteArray) : FetchResult
}

sealed interface PutResult {
    data class Stored(val version: Long) : PutResult
    data class Conflict(val version: Long) : PutResult
}

/** What the engine needs from the server; [RetrofitSyncTransport] in the app, a fake in tests. */
interface SyncTransport {
    /** Version per stored collection. Throws [SyncGroupGoneException] for an unknown group. */
    suspend fun list(group: SyncGroup): Map<String, Long>
    suspend fun fetch(group: SyncGroup, collection: String, knownVersion: Long?): FetchResult
    suspend fun put(group: SyncGroup, collection: String, sealed: ByteArray, expectedVersion: Long, deviceId: String): PutResult
    suspend fun delete(group: SyncGroup)
}

class RetrofitSyncTransport(private val service: SyncService, private val deviceId: String? = null) : SyncTransport {
    private val octets = "application/octet-stream".toMediaType()

    override suspend fun list(group: SyncGroup): Map<String, Long> {
        val response = service.list(group.groupId, group.authToken, deviceId)
        if (response.code() == 404) throw SyncGroupGoneException()
        val body = response.body()
        if (!response.isSuccessful || body == null) throw failure(response)
        return body.collections.associate { it.name to it.version }
    }

    override suspend fun fetch(group: SyncGroup, collection: String, knownVersion: Long?): FetchResult {
        val response = service.fetch(group.groupId, collection, group.authToken, knownVersion?.let { "\"$it\"" }, deviceId)
        return when {
            response.code() == 304 -> FetchResult.NotModified
            response.code() == 404 -> {
                val code = response.errorBody()?.string().orEmpty()
                if ("SYNC_GROUP_NOT_FOUND" in code) throw SyncGroupGoneException()
                FetchResult.Empty
            }
            response.isSuccessful -> {
                val version = parseVersion(response.headers()["ETag"]) ?: throw failure(response)
                FetchResult.Blob(version, response.body()?.bytes() ?: ByteArray(0))
            }
            else -> throw failure(response)
        }
    }

    override suspend fun put(group: SyncGroup, collection: String, sealed: ByteArray, expectedVersion: Long, deviceId: String): PutResult {
        val response = service.put(
            groupId = group.groupId,
            collection = collection,
            auth = group.authToken,
            ifMatch = "\"$expectedVersion\"",
            device = deviceId,
            body = sealed.toRequestBody(octets)
        )
        val version = parseVersion(response.headers()["ETag"])
        return when {
            response.code() == 412 && version != null -> PutResult.Conflict(version)
            response.isSuccessful && version != null -> PutResult.Stored(version)
            response.code() == 404 -> {
                val body = response.errorBody()?.string().orEmpty()
                when {
                    // A write expecting a later version to a group the server no longer knows: deleted.
                    "SYNC_GROUP_NOT_FOUND" in body -> throw SyncGroupGoneException()
                    "UNKNOWN_SYNC_COLLECTION" in body -> throw SyncCollectionUnknownException(collection)
                    else -> throw failure(response)
                }
            }
            else -> throw failure(response)
        }
    }

    override suspend fun delete(group: SyncGroup) {
        val response = service.delete(group.groupId, group.authToken, deviceId)
        // Already gone counts as deleted.
        if (!response.isSuccessful && response.code() != 404) throw failure(response)
    }

    private fun failure(response: Response<*>): Exception =
        SyncAccountException.fromResponse(response.code(), runCatching { response.errorBody()?.string() }.getOrNull())
            ?: SyncTransportException(response.code(), "HTTP ${response.code()}")

    private fun parseVersion(etag: String?): Long? =
        etag?.removePrefix("W/")?.trim('"')?.toLongOrNull()
}
