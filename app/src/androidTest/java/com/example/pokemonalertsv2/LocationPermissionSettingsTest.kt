package com.example.pokemonalertsv2

import android.Manifest
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocationPermissionSettingsTest {

    @Test
    fun directIntentTargetsThisAppsLocationPermissionGroup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = directAppLocationPermissionSettingsIntent(context.packageName)

        assertEquals(context.packageName, intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME))
        assertEquals(
            Manifest.permission_group.LOCATION,
            intent.getStringExtra(Intent.EXTRA_PERMISSION_GROUP_NAME)
        )
        assertNotNull(intent.resolveActivity(context.packageManager))
    }

    @Test
    fun appPermissionsIntentTargetsThisApp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = appPermissionsSettingsIntent(context.packageName)

        assertEquals("android.intent.action.MANAGE_APP_PERMISSIONS", intent.action)
        assertEquals(context.packageName, intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME))
    }

    @Test
    fun launchAttemptsDirectLocationPageWithoutResolveGate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val launched = mutableListOf<Intent>()

        val result = launchAppLocationPermissionSettings(
            context = context,
            launch = launched::add
        )

        assertEquals(LocationSettingsLaunchResult.DIRECT, result)
        assertEquals(listOf("android.intent.action.MANAGE_APP_PERMISSION"), launched.map { it.action })
    }

    @Test
    fun securityExceptionFromDirectPageFallsBackToAppPermissions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val launched = mutableListOf<String?>()

        val result = launchAppLocationPermissionSettings(
            context = context,
            launch = { intent ->
                launched += intent.action
                if (intent.action == "android.intent.action.MANAGE_APP_PERMISSION") {
                    throw SecurityException("OEM denied hidden permission activity")
                }
            }
        )

        assertEquals(LocationSettingsLaunchResult.APP_PERMISSIONS, result)
        assertEquals(
            listOf("android.intent.action.MANAGE_APP_PERMISSION", "android.intent.action.MANAGE_APP_PERMISSIONS"),
            launched
        )
    }

    @Test
    fun rejectedPermissionPagesFallBackToAppDetails() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val launched = mutableListOf<String?>()

        val result = launchAppLocationPermissionSettings(
            context = context,
            launch = { intent ->
                launched += intent.action
                if (intent.action != android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS) {
                    throw SecurityException("Permission page unavailable")
                }
            }
        )

        assertEquals(LocationSettingsLaunchResult.APP_DETAILS, result)
        assertEquals(3, launched.size)
        assertEquals(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, launched.last())
    }

    @Test
    fun allSettingsLaunchesFailWithoutThrowing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val result = launchAppLocationPermissionSettings(
            context = context,
            launch = { throw SecurityException("No settings activity available") }
        )

        assertEquals(LocationSettingsLaunchResult.FAILED, result)
    }
}
