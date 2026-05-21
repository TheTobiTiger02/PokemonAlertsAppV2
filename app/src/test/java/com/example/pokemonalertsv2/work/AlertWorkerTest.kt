package com.example.pokemonalertsv2.work

import androidx.work.ExistingWorkPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class AlertWorkerTest {

    @Test
    fun immediateSyncPolicy_keepsAlreadyRunningWork() {
        assertEquals(ExistingWorkPolicy.KEEP, AlertWorker.immediateSyncPolicy)
    }
}
