package com.example.pokemonalertsv2

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.outlined.Build

private val SUPPORTED_EXTERNAL_CSV_MIME_TYPES = setOf(
    "text/csv",
    "text/comma-separated-values",
    "application/csv",
    "application/vnd.ms-excel"
)

/** Returns the one externally supplied CSV URI for supported open/share actions. */
internal fun externalCsvUri(intent: Intent): Uri? = when (intent.action) {
    Intent.ACTION_VIEW -> intent.data
    Intent.ACTION_SEND -> intent.sharedStreamUri() ?: intent.clipData
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.uri
    else -> null
}

@Suppress("DEPRECATION")
internal fun Intent.sharedStreamUri(): Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
} else {
    getParcelableExtra(Intent.EXTRA_STREAM)
}

internal fun isSupportedCsvIntent(intent: Intent, uri: Uri): Boolean {
    val mime = intent.type?.lowercase()?.substringBefore(';')
    val mimeSupported = mime in SUPPORTED_EXTERNAL_CSV_MIME_TYPES
    val extensionSupported = uri.lastPathSegment
        ?.substringBefore('?')
        ?.endsWith(".csv", ignoreCase = true) == true
    return mimeSupported || extensionSupported
}
