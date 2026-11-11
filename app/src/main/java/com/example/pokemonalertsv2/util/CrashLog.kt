package com.example.pokemonalertsv2.util

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.example.pokemonalertsv2.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last few crashes on the device so they can be shared from Settings.
 *
 * Nothing leaves the phone unless the user shares a report. The handler writes a
 * small text file and then hands the crash to the platform's handler, so the app
 * still dies exactly as before.
 */
object CrashLog {
    private const val DIRECTORY = "crashes"
    private const val KEEP = 5

    fun install(context: Context) {
        val directory = directory(context)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(directory, thread, error, System.currentTimeMillis()) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun latest(context: Context): File? =
        directory(context).listFiles { file -> file.name.endsWith(".txt") }?.maxByOrNull { it.name }

    /** A share sheet for the newest report, or null when there is none. */
    fun shareIntent(context: Context): Intent? {
        val report = latest(context) ?: return null
        val shared = File(context.cacheDir, "crash-report.txt").apply { writeText(report.readText()) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shared)
        return Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                // ClipData carries the read grant to the chooser itself, not just the target app.
                clipData = android.content.ClipData.newRawUri(null, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Pokémon Alerts crash report ${BuildConfig.VERSION_NAME}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            null
        )
    }

    internal fun write(directory: File, thread: Thread, error: Throwable, nowMillis: Long): File {
        directory.mkdirs()
        val stack = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date(nowMillis))
        val file = File(directory, "crash-$stamp.txt")
        file.writeText(
            buildString {
                appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.BUILD_TYPE}")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("Thread: ${thread.name}")
                appendLine("Time: $stamp")
                appendLine()
                append(stack)
            }
        )
        directory.listFiles { candidate -> candidate.name.endsWith(".txt") }
            ?.sortedByDescending { it.name }
            ?.drop(KEEP)
            ?.forEach { it.delete() }
        return file
    }

    private fun directory(context: Context) = File(context.filesDir, DIRECTORY)
}
