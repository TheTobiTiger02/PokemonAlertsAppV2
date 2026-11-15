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
import retrofit2.http.PUT
import retrofit2.http.Path

/** The backend's device sync routes (DEVICE_SYNC.md in AlsbachScanner). */
interface SyncService {
    @GET("api/sync/{groupId}")
    suspend fun list(
        @Path("groupId") groupId: String,
        @Header("X-Sync-Auth") auth: String,
        @Header("X-App-Key") appKey: String? = appKey()
    ): Response<SyncCollectionList>

    @GET("api/sync/{groupId}/{collection}")
    suspend fun fetch(
        @Path("groupId") groupId: String,
        @Path("collection") collection: String,
        @Header("X-Sync-Auth") auth: String,
        @Header("If-None-Match") ifNoneMatch: String?,
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
        @Header("X-App-Key") appKey: String? = appKey()
    ): Response<ResponseBody>
}

private fun appKey(): String? = BuildConfig.APP_API_KEY.takeIf { it.isNotBlank() }

@Serializable
data class SyncCollectionList(val collections: List<SyncCollectionInfo> = emptyList())

@Serializable
data class SyncCollectionInfo(val name: String, val version: Long = 0, val updatedAt: String? = null, val bytes: Long = 0)

/** The server no longer knows the group: deleted from another device, or purged when idle. */
class SyncGroupGoneException : Exception("Sync group not found")

/** Anything else the server refused or could not answer. */
class SyncTransportException(val status: Int, message: String) : Exception(message)

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

class RetrofitSyncTransport(private val service: SyncService) : SyncTransport {
    private val octets = "application/octet-stream".toMediaType()

    override suspend fun list(group: SyncGroup): Map<String, Long> {
        val response = service.list(group.groupId, group.authToken)
        if (response.code() == 404) throw SyncGroupGoneException()
        val body = response.body()
        if (!response.isSuccessful || body == null) throw failure(response)
        return body.collections.associate { it.name to it.version }
    }

    override suspend fun fetch(group: SyncGroup, collection: String, knownVersion: Long?): FetchResult {
        val response = service.fetch(group.groupId, collection, group.authToken, knownVersion?.let { "\"$it\"" })
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
            else -> throw failure(response)
        }
    }

    override suspend fun delete(group: SyncGroup) {
        val response = service.delete(group.groupId, group.authToken)
        // Already gone counts as deleted.
        if (!response.isSuccessful && response.code() != 404) throw failure(response)
    }

    private fun failure(response: Response<*>) =
        SyncTransportException(response.code(), "HTTP ${response.code()}")

    private fun parseVersion(etag: String?): Long? =
        etag?.removePrefix("W/")?.trim('"')?.toLongOrNull()
}
