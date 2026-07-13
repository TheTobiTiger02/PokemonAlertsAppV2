package com.example.pokemonalertsv2

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

private const val ACTION_MANAGE_APP_PERMISSION =
    "android.intent.action.MANAGE_APP_PERMISSION"

internal fun directAppLocationPermissionSettingsIntent(packageName: String): Intent =
    Intent(ACTION_MANAGE_APP_PERMISSION).apply {
        putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
        putExtra(Intent.EXTRA_PERMISSION_GROUP_NAME, Manifest.permission_group.LOCATION)
    }

internal fun appDetailsSettingsIntent(packageName: String): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:$packageName")
    }

internal fun appLocationPermissionSettingsIntent(
    context: Context,
    canResolveDirectIntent: (Intent) -> Boolean = { intent ->
        intent.resolveActivity(context.packageManager) != null
    }
): Intent {
    val directIntent = directAppLocationPermissionSettingsIntent(context.packageName)
    return if (canResolveDirectIntent(directIntent)) {
        directIntent
    } else {
        appDetailsSettingsIntent(context.packageName)
    }
}

internal enum class LocationSettingsLaunchResult {
    DIRECT,
    APP_DETAILS,
    FAILED
}

/**
 * The permission-controller action is hidden and some OEMs resolve it but deny
 * third-party launches. Always attempt it defensively and fall back to the
 * public app-details page without letting an OEM exception escape.
 */
internal fun launchAppLocationPermissionSettings(
    context: Context,
    canResolveDirectIntent: (Intent) -> Boolean = { intent ->
        intent.resolveActivity(context.packageManager) != null
    },
    launch: (Intent) -> Unit
): LocationSettingsLaunchResult {
    val directIntent = directAppLocationPermissionSettingsIntent(context.packageName)
    if (canResolveDirectIntent(directIntent)) {
        try {
            launch(directIntent)
            return LocationSettingsLaunchResult.DIRECT
        } catch (_: RuntimeException) {
            // Fall through to the public, OEM-safe app details page.
        }
    }

    return try {
        launch(appDetailsSettingsIntent(context.packageName))
        LocationSettingsLaunchResult.APP_DETAILS
    } catch (_: RuntimeException) {
        LocationSettingsLaunchResult.FAILED
    }
}
