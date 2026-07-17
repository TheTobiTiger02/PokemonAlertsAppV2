package com.example.pokemonalertsv2

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

private const val ACTION_MANAGE_APP_PERMISSION =
    "android.intent.action.MANAGE_APP_PERMISSION"
private const val ACTION_MANAGE_APP_PERMISSIONS =
    "android.intent.action.MANAGE_APP_PERMISSIONS"

internal fun directAppLocationPermissionSettingsIntent(packageName: String): Intent =
    Intent(ACTION_MANAGE_APP_PERMISSION).apply {
        putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
        putExtra(Intent.EXTRA_PERMISSION_GROUP_NAME, Manifest.permission_group.LOCATION)
    }

internal fun appPermissionsSettingsIntent(packageName: String): Intent =
    Intent(ACTION_MANAGE_APP_PERMISSIONS).apply {
        putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
    }

internal fun appDetailsSettingsIntent(packageName: String): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:$packageName")
    }

internal enum class LocationSettingsLaunchResult {
    DIRECT,
    APP_PERMISSIONS,
    APP_DETAILS,
    FAILED
}

/**
 * Permission-controller activities may be hidden from package visibility even
 * when Android can launch them. Attempt the direct actions in order and only
 * fall back to app details when the OEM rejects both permission pages.
 */
internal fun launchAppLocationPermissionSettings(
    context: Context,
    launch: (Intent) -> Unit
): LocationSettingsLaunchResult {
    val attempts = listOf(
        LocationSettingsLaunchResult.DIRECT to
            directAppLocationPermissionSettingsIntent(context.packageName),
        LocationSettingsLaunchResult.APP_PERMISSIONS to
            appPermissionsSettingsIntent(context.packageName),
        LocationSettingsLaunchResult.APP_DETAILS to
            appDetailsSettingsIntent(context.packageName)
    )
    attempts.forEach { (result, intent) ->
        try {
            launch(intent)
            return result
        } catch (_: RuntimeException) {
            // Try the next, less specific settings surface.
        }
    }
    return LocationSettingsLaunchResult.FAILED
}
