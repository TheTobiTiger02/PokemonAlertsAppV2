package com.example.pokemonalertsv2

import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pokemonalertsv2.data.AlertPreferences
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalCsvIntentInstrumentedTest {

    @Test
    fun extractsCsvFromViewSendAndClipData() {
        val uri = Uri.parse("content://pokegenie/export/scan-data.csv")

        assertEquals(uri, externalCsvUri(Intent(Intent.ACTION_VIEW, uri)))
        assertEquals(
            uri,
            externalCsvUri(
                Intent(Intent.ACTION_SEND)
                    .setType("text/csv")
                    .putExtra(Intent.EXTRA_STREAM, uri)
            )
        )
        assertEquals(
            uri,
            externalCsvUri(
                Intent(Intent.ACTION_SEND)
                    .setType("text/csv")
                    .apply { clipData = ClipData.newRawUri("Poké Genie CSV", uri) }
            )
        )
        assertNull(externalCsvUri(Intent(Intent.ACTION_SEND).setType("text/csv")))
        assertNull(externalCsvUri(Intent(Intent.ACTION_SEND_MULTIPLE).setType("text/csv")))
    }

    @Test
    fun acceptsKnownMimeOrCsvExtensionButRejectsUnrelatedShares() {
        val csv = Uri.parse("content://pokegenie/export/scan-data.CSV")
        val text = Uri.parse("content://example/export/notes.txt")

        assertTrue(isSupportedCsvIntent(Intent().setType("text/csv"), text))
        assertTrue(isSupportedCsvIntent(Intent().setType("application/octet-stream"), csv))
        assertFalse(isSupportedCsvIntent(Intent().setType("text/plain"), text))
    }

    @Test
    fun manifestOffersMainActivityOnlyForSupportedCsvShares() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageManager = context.packageManager

        listOf(
            "text/csv",
            "text/comma-separated-values",
            "application/csv",
            "application/vnd.ms-excel"
        ).forEach { mime ->
            assertTrue(
                mime,
                packageManager.queryIntentActivities(
                    Intent(Intent.ACTION_SEND).setType(mime),
                    PackageManager.MATCH_DEFAULT_ONLY
                ).any { it.activityInfo.name == MainActivity::class.java.name }
            )
        }
        assertFalse(
            packageManager.queryIntentActivities(
                Intent(Intent.ACTION_SEND).setType("text/plain"),
                PackageManager.MATCH_DEFAULT_ONLY
            ).any { it.activityInfo.name == MainActivity::class.java.name }
        )
    }

    @Test
    fun coldStartSharePreparesTheCsvCandidate() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val preferences = AlertPreferences(context.alertPreferencesDataStore)
        val onboardingWasComplete = runBlocking { preferences.onboardingCompleted.first() }
        val file = context.cacheDir.resolve("pokegenie-cold-start.csv").apply {
            writeText("Name,CP\nMewtwo,2387\n")
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_SEND)
            .setType("text/csv")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        runBlocking { preferences.setOnboardingCompleted(true) }
        try {
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                var candidateName: String? = null
                val deadline = SystemClock.uptimeMillis() + 10_000L
                while (candidateName == null && SystemClock.uptimeMillis() < deadline) {
                    scenario.onActivity {
                        candidateName = it.pendingExternalCsvImportForTest()?.fileName
                    }
                    if (candidateName == null) SystemClock.sleep(100)
                }
                assertEquals(file.name, candidateName)
            }
        } finally {
            runBlocking { preferences.setOnboardingCompleted(onboardingWasComplete) }
            file.delete()
        }
    }
}
