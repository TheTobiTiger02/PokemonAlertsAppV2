package com.example.pokemonalertsv2.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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

sealed interface UpdateState {
    object Idle : UpdateState
    object Checking : UpdateState
    data class UpdateAvailable(val release: GitHubRelease) : UpdateState
    object UpToDate : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    object Installing : UpdateState
    data class Error(val message: String) : UpdateState
}

object InAppUpdateManager {
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val client = OkHttpClient.Builder().build()

    fun resetState() {
        _updateState.value = UpdateState.Idle
    }

    suspend fun checkForUpdates(owner: String = "TheTobiTiger02", repo: String = "PokemonAlertsAppV2") {
        _updateState.value = UpdateState.Checking
        withContext(Dispatchers.IO) {
            try {
                val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "PokemonAlertsApp")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        _updateState.value = UpdateState.Error("Failed to fetch release info: ${response.code}")
                        return@withContext
                    }
                    val bodyString = response.body?.string() ?: throw IOException("Empty response body")
                    val release = json.decodeFromString<GitHubRelease>(bodyString)
                    
                    val currentVersion = BuildConfig.VERSION_NAME
                    if (isNewerVersion(currentVersion, release.tagName)) {
                        _updateState.value = UpdateState.UpdateAvailable(release)
                    } else {
                        _updateState.value = UpdateState.UpToDate
                    }
                }
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error(e.localizedMessage ?: "Unknown error checking updates")
            }
        }
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

        _updateState.value = UpdateState.Downloading(0f)
        
        withContext(Dispatchers.IO) {
            try {
                val outputFile = File(context.cacheDir, "pokemon-alerts-update.apk")
                if (outputFile.exists()) {
                    outputFile.delete()
                }

                val request = Request.Builder()
                    .url(apkAsset.browserDownloadUrl)
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
                        _updateState.value = UpdateState.Idle
                    } else {
                        // Store the file in a variable or just let the trigger fail and show permission
                        _updateState.value = UpdateState.Error("Unknown sources permission required")
                    }
                }
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error(e.localizedMessage ?: "Failed to download update")
            }
        }
    }

    fun canRequestInstall(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun launchUnknownSourcesSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
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
}
