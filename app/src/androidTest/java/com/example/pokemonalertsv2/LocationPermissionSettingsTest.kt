package com.example.pokemonalertsv2

import android.Manifest
import android.content.Intent
import android.provider.Settings
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
    fun unresolvedDirectIntentFallsBackToAppDetails() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = appLocationPermissionSettingsIntent(context) { false }

        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package:${context.packageName}", intent.dataString)
    }

    @Test
    fun launchUsesDirectLocationPageWhenAllowed() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val launched = mutableListOf<Intent>()

        val result = launchAppLocationPermissionSettings(
            context = context,
            canResolveDirectIntent = { true },
            launch = launched::add
        )

        assertEquals(LocationSettingsLaunchResult.DIRECT, result)
        assertEquals(listOf("android.intent.action.MANAGE_APP_PERMISSION"), launched.map { it.action })
    }

    @Test
    fun securityExceptionFromDirectPageFallsBackToAppDetails() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val launched = mutableListOf<String?>()

        val result = launchAppLocationPermissionSettings(
            context = context,
            canResolveDirectIntent = { true },
            launch = { intent ->
                launched += intent.action
                if (intent.action == "android.intent.action.MANAGE_APP_PERMISSION") {
                    throw SecurityException("OEM denied hidden permission activity")
                }
            }
        )

        assertEquals(LocationSettingsLaunchResult.APP_DETAILS, result)
        assertEquals(
            listOf("android.intent.action.MANAGE_APP_PERMISSION", Settings.ACTION_APPLICATION_DETAILS_SETTINGS),
            launched
        )
    }

    @Test
    fun bothSettingsLaunchesFailWithoutThrowing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val result = launchAppLocationPermissionSettings(
            context = context,
            canResolveDirectIntent = { true },
            launch = { throw SecurityException("No settings activity available") }
        )

        assertEquals(LocationSettingsLaunchResult.FAILED, result)
    }
}
