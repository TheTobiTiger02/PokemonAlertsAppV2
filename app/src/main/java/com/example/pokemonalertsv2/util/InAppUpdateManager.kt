package com.example.pokemonalertsv2.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.pokemonalertsv2.BuildConfig
import com.example.pokemonalertsv2.data.GitHubRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

enum class UpdateCheckSource {
    AUTOMATIC,
    MANUAL
}

sealed interface UpdateState {
    object Idle : UpdateState
    object Checking : UpdateState
    data class UpdateAvailable(val release: GitHubRelease) : UpdateState
    object UpToDate : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    object Installing : UpdateState
    data class AwaitingInstallPermission(val releaseTag: String) : UpdateState
    data class Error(val message: String) : UpdateState
}

data class PendingInstall(
    val releaseTag: String,
    val apkUrl: String,
    val apkPath: String
)

object InAppUpdateManager {
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()
    private val updateCheckInFlight = AtomicBoolean(false)

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val client = OkHttpClient.Builder().build()

    fun resetState() {
        _updateState.value = UpdateState.Idle
    }

    suspend fun checkForUpdates(
        source: UpdateCheckSource,
        owner: String = "TheTobiTiger02",
        repo: String = "PokemonAlertsAppV2"
    ) {
        if (!canStartUpdateCheck(_updateState.value)) return
        if (!updateCheckInFlight.compareAndSet(false, true)) return
        _updateState.value = UpdateState.Checking
        try {
            withContext(Dispatchers.IO) {
                val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "PokemonAlertsApp")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        finishCheckWithError(source, "Failed to fetch release info: ${response.code}")
                        return@withContext
                    }
                    val bodyString = response.body?.string() ?: throw IOException("Empty response body")
                    val release = json.decodeFromString<GitHubRelease>(bodyString)
                    
                    val currentVersion = BuildConfig.VERSION_NAME
                    if (isNewerVersion(currentVersion, release.tagName)) {
                        _updateState.value = UpdateState.UpdateAvailable(release)
                    } else {
                        _updateState.value = noUpdateState(source)
                    }
                }
            }
        } catch (e: Exception) {
            finishCheckWithError(source, e.localizedMessage ?: "Unknown error checking updates")
        } finally {
            updateCheckInFlight.set(false)
        }
    }

    private fun finishCheckWithError(source: UpdateCheckSource, message: String) {
        if (source == UpdateCheckSource.AUTOMATIC) {
            Log.w(TAG, message)
        }
        _updateState.value = updateErrorState(source, message)
    }

    fun isNewerVersion(current: String, latest: String): Boolean {
        val cleanCurrent = current.trim().removePrefix("v").split("-").first()
        val cleanLatest = latest.trim().removePrefix("v").split("-").first()
        
        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = cleanLatest.split(".").mapNotNull { it.toIntOrNull() }
        
        val maxParts = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until maxParts) {
            val currentVal = currentParts.getOrElse(i) { 0 }
            val latestVal = latestParts.getOrElse(i) { 0 }
            if (latestVal > currentVal) return true
            if (currentVal > latestVal) return false
        }
        return false
    }

    suspend fun downloadAndInstall(context: Context, release: GitHubRelease) {
        val apkAsset = release.assets.find { it.name.endsWith(".apk") }
        if (apkAsset == null) {
            _updateState.value = UpdateState.Error("No APK asset found in the latest release")
            return
        }

        val outputFile = File(context.cacheDir, UPDATE_APK_NAME)
        val pendingInstall = PendingInstall(
            releaseTag = release.tagName,
            apkUrl = apkAsset.browserDownloadUrl,
            apkPath = outputFile.absolutePath
        )
        PendingInstallStore.save(context, pendingInstall)
        downloadPendingInstall(context, pendingInstall)
    }

    private suspend fun downloadPendingInstall(context: Context, pendingInstall: PendingInstall) {
        _updateState.value = UpdateState.Downloading(0f)
        withContext(Dispatchers.IO) {
            try {
                val outputFile = File(pendingInstall.apkPath)
                if (outputFile.exists()) {
                    outputFile.delete()
                }

                val request = Request.Builder()
                    .url(pendingInstall.apkUrl)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Failed to download APK: ${response.code}")
                    }
                    val body = response.body ?: throw IOException("Empty response body")
                    val contentLength = body.contentLength()
                    
                    body.byteStream().use { inputStream ->
                        FileOutputStream(outputFile).use { outputStream ->
                            val buffer = ByteArray(4096)
                            var bytesRead: Int
                            var totalBytesRead = 0L
                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead
                                if (contentLength > 0) {
                                    val progress = totalBytesRead.toFloat() / contentLength
                                    _updateState.value = UpdateState.Downloading(progress)
                                }
                            }
                        }
                    }
                }

                _updateState.value = UpdateState.Installing
                withContext(Dispatchers.Main) {
                    if (canRequestInstall(context)) {
                        triggerInstall(context, outputFile)
                        PendingInstallStore.clear(context)
                        _updateState.value = UpdateState.Idle
                    } else {
                        _updateState.value = UpdateState.AwaitingInstallPermission(pendingInstall.releaseTag)
                    }
                }
            } catch (e: Exception) {
                PendingInstallStore.clear(context)
                _updateState.value = UpdateState.Error(e.localizedMessage ?: "Failed to download update")
            }
        }
    }

    suspend fun restorePendingInstall(context: Context): Boolean {
        val pendingInstall = PendingInstallStore.load(context) ?: return false
        if (canRequestInstall(context)) {
            resumePendingInstall(context)
        } else {
            _updateState.value = UpdateState.AwaitingInstallPermission(pendingInstall.releaseTag)
        }
        return true
    }

    suspend fun resumePendingInstall(context: Context) {
        val pendingInstall = PendingInstallStore.load(context)
        if (pendingInstall == null) {
            _updateState.value = UpdateState.Error("The pending update is no longer available")
            return
        }
        if (!canRequestInstall(context)) {
            _updateState.value = UpdateState.AwaitingInstallPermission(pendingInstall.releaseTag)
            return
        }
        val apkFile = File(pendingInstall.apkPath)
        if (!apkFile.exists() || apkFile.length() <= 0L) {
            downloadPendingInstall(context, pendingInstall)
            return
        }
        _updateState.value = UpdateState.Installing
        withContext(Dispatchers.Main) {
            triggerInstall(context, apkFile)
            PendingInstallStore.clear(context)
            _updateState.value = UpdateState.Idle
        }
    }

    fun cancelPendingInstall(context: Context) {
        PendingInstallStore.load(context)?.apkPath?.let { path -> runCatching { File(path).delete() } }
        PendingInstallStore.clear(context)
        resetState()
    }

    fun canRequestInstall(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun unknownSourcesSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    fun triggerInstall(context: Context, apkFile: File) {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }

    private const val UPDATE_APK_NAME = "pokemon-alerts-update.apk"
    private const val TAG = "InAppUpdateManager"
}

internal fun canStartUpdateCheck(state: UpdateState): Boolean = when (state) {
    UpdateState.Idle,
    UpdateState.UpToDate,
    is UpdateState.Error -> true
    UpdateState.Checking,
    is UpdateState.UpdateAvailable,
    is UpdateState.Downloading,
    UpdateState.Installing,
    is UpdateState.AwaitingInstallPermission -> false
}

internal fun noUpdateState(source: UpdateCheckSource): UpdateState =
    if (source == UpdateCheckSource.MANUAL) UpdateState.UpToDate else UpdateState.Idle

internal fun updateErrorState(source: UpdateCheckSource, message: String): UpdateState =
    if (source == UpdateCheckSource.MANUAL) UpdateState.Error(message) else UpdateState.Idle

internal object PendingInstallStore {
    private const val PREFS = "pending_update_install"
    private const val KEY_RELEASE_TAG = "release_tag"
    private const val KEY_APK_URL = "apk_url"
    private const val KEY_APK_PATH = "apk_path"

    fun save(context: Context, pendingInstall: PendingInstall) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RELEASE_TAG, pendingInstall.releaseTag)
            .putString(KEY_APK_URL, pendingInstall.apkUrl)
            .putString(KEY_APK_PATH, pendingInstall.apkPath)
            .apply()
    }

    fun load(context: Context): PendingInstall? {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val releaseTag = preferences.getString(KEY_RELEASE_TAG, null) ?: return null
        val apkUrl = preferences.getString(KEY_APK_URL, null) ?: return null
        val apkPath = preferences.getString(KEY_APK_PATH, null) ?: return null
        return PendingInstall(releaseTag, apkUrl, apkPath)
    }

    fun hasPending(context: Context): Boolean = load(context) != null

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
