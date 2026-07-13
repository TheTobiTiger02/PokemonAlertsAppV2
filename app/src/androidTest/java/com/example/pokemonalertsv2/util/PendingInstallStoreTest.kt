package com.example.pokemonalertsv2.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PendingInstallStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun tearDown() {
        PendingInstallStore.clear(context)
    }

    @Test
    fun pendingInstallSurvivesManagerStateRecreation() {
        val pending = PendingInstall("v2.0.0", "https://example.test/app.apk", "/cache/app.apk")
        PendingInstallStore.save(context, pending)
        assertEquals(pending, PendingInstallStore.load(context))
    }

    @Test
    fun cancellationClearsPendingInstall() {
        PendingInstallStore.save(
            context,
            PendingInstall("v2.0.0", "https://example.test/app.apk", "/cache/app.apk")
        )
        PendingInstallStore.clear(context)
        assertNull(PendingInstallStore.load(context))
    }
}
