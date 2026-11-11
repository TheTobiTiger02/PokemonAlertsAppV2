package com.example.pokemonalertsv2.fcm

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FcmTopicReconcileTest {
    private val calls = mutableListOf<String>()

    private suspend fun reconcile(
        applied: Set<String>?,
        desired: Set<String>,
        failing: Set<String> = emptySet(),
    ): Set<String> = FcmTopicSubscriptionManager.reconcileTopics(
        applied = applied,
        desired = desired,
        legacyTopic = "alerts",
        subscribe = { calls += "+$it"; if (it in failing) error("subscribe $it") },
        unsubscribe = { calls += "-$it"; if (it in failing) error("unsubscribe $it") },
        warn = { _, _ -> },
    )

    @Test
    fun firstSyncLeavesTheLegacyTopic() = runTest {
        val result = reconcile(applied = null, desired = setOf("alerts-raid"))
        assertEquals(setOf("alerts-raid"), result)
        assertEquals(listOf("+alerts-raid", "-alerts"), calls)
    }

    @Test
    fun firstSyncKeepsTheLegacyTopicWhenItIsStillWanted() = runTest {
        val result = reconcile(applied = null, desired = setOf("alerts"))
        assertEquals(setOf("alerts"), result)
        assertEquals(listOf("+alerts"), calls)
    }

    @Test
    fun onlyTheDifferenceIsSent() = runTest {
        val result = reconcile(applied = setOf("a", "b"), desired = setOf("b", "c"))
        assertEquals(setOf("b", "c"), result)
        assertEquals(listOf("+c", "-a"), calls)
    }

    @Test
    fun subscribesBeforeUnsubscribing() = runTest {
        reconcile(applied = setOf("old1", "old2"), desired = setOf("new1", "new2"))
        val firstUnsubscribe = calls.indexOfFirst { it.startsWith("-") }
        assertTrue(calls.take(firstUnsubscribe).all { it.startsWith("+") })
        assertEquals(2, firstUnsubscribe)
    }

    @Test
    fun aFailedSubscribeIsLeftOutSoTheNextSyncRetriesIt() = runTest {
        val result = reconcile(applied = emptySet(), desired = setOf("a", "b"), failing = setOf("b"))
        assertEquals(setOf("a"), result)
    }

    @Test
    fun aFailedUnsubscribeIsKeptSoTheNextSyncRetriesIt() = runTest {
        val result = reconcile(applied = setOf("a", "gone"), desired = setOf("a"), failing = setOf("gone"))
        assertEquals(setOf("a", "gone"), result)
    }

    @Test
    fun anEmptyDesiredSetUnsubscribesEverything() = runTest {
        val result = reconcile(applied = setOf("a", "b"), desired = emptySet())
        assertEquals(emptySet<String>(), result)
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsNotSwallowed() = runTest {
        FcmTopicSubscriptionManager.reconcileTopics(
            applied = emptySet(),
            desired = setOf("a"),
            legacyTopic = "alerts",
            subscribe = { throw CancellationException("cancelled") },
            unsubscribe = {},
            warn = { _, _ -> },
        )
    }
}
