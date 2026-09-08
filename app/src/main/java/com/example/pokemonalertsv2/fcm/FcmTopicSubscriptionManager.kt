package com.example.pokemonalertsv2.fcm

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.example.pokemonalertsv2.data.AlertPreferences
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import com.example.pokemonalertsv2.data.FilterDefinition
import com.example.pokemonalertsv2.data.PushTopicsRepository
import com.example.pokemonalertsv2.data.surfaceDefinitions
import com.example.pokemonalertsv2.data.unionOf
import com.example.pokemonalertsv2.widget.AlertsWidgetProvider
import com.example.pokemonalertsv2.widget.WidgetConfigurationStore
import com.example.pokemonalertsv2.widget.legacyFilterDefinition
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.flow.first
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Keeps the device's FCM topic subscriptions equal to what [PushTopicPlanner] asks for.
 *
 * Replaces the old unconditional `subscribeToTopic("alerts")`. The applied set is persisted so
 * a sync is a diff rather than a full re-subscribe, and so a partial failure is retried next
 * time instead of being silently forgotten.
 */
object FcmTopicSubscriptionManager {
    private const val TAG = "FcmTopicSubscriber"
    private const val PREFS_NAME = "fcm_topic_state"
    private const val APPLIED_TOPICS_KEY = "applied_topics"

    suspend fun sync(context: Context) {
        val appContext = context.applicationContext
        runCatching {
            FirebaseApp.initializeApp(appContext)
            val catalog = PushTopicsRepository.getInstance(appContext).refresh()
            val legacyTopic = catalog?.legacyTopic?.takeIf { it.isNotBlank() }
                ?: PushTopicPlanner.DEFAULT_LEGACY_TOPIC
            val desired = PushTopicPlanner.plan(catalog, unionDefinition(appContext))
            apply(appContext, desired, legacyTopic)
        }.onFailure { exception ->
            Log.w(TAG, "FCM topic sync failed; leaving the current subscription in place", exception)
        }
    }

    /**
     * Topic subscriptions are per-token, so a rotated token starts with none of them. Recording
     * an explicitly empty set makes the next [sync] re-subscribe everything, and is deliberately
     * not the same as the key being absent — see [appliedTopics].
     */
    fun forgetAppliedTopics(context: Context) {
        prefs(context).edit().putStringSet(APPLIED_TOPICS_KEY, emptySet()).apply()
    }

    /** What this device is believed to be subscribed to, or null when it has never synced. */
    private fun appliedTopics(context: Context): Set<String>? =
        prefs(context).getStringSet(APPLIED_TOPICS_KEY, null)?.toSet()

    /**
     * The union of every surface that can show an alert. FCM is the only thing that wakes the
     * app in the background, so a topic nobody subscribed to leaves the feed, map, widgets and
     * history unaware of the alert until the next foreground refresh — not merely un-notified.
     */
    private suspend fun unionDefinition(context: Context): FilterDefinition {
        val document = AlertPreferences(context.alertPreferencesDataStore).filterStateDocument.first()
        return unionOf(document.surfaceDefinitions() + widgetDefinitions(context, document))
    }

    private fun widgetDefinitions(
        context: Context,
        document: com.example.pokemonalertsv2.data.FilterStateDocument
    ): List<FilterDefinition> = runCatching {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, AlertsWidgetProvider::class.java))
        ids.map { id ->
            val configuration = WidgetConfigurationStore.get(context, id)
            configuration.filterAssignment?.resolve(document)
                ?: configuration.legacyFilterDefinition(appArea = "All", appDistanceKm = 0)
        }
    }.getOrDefault(emptyList())

    private suspend fun apply(context: Context, desired: Set<String>, legacyTopic: String) {
        val messaging = FirebaseMessaging.getInstance()
        val applied = appliedTopics(context)
        if (applied == desired) {
            Log.d(TAG, "FCM topics already current: ${desired.sorted()}")
            return
        }

        // A never-synced install is either fresh or an upgrade from the build that subscribed to
        // the legacy topic unconditionally. Assuming the latter is what lets the first sync take
        // the device off it; on a fresh install the unsubscribe is a harmless no-op. Leaving a
        // device on both the legacy and the derived topics would deliver some alerts twice.
        val current = (applied ?: emptySet()).toMutableSet()
        val stale = ((applied ?: setOf(legacyTopic)) - desired).toMutableSet()

        // Subscribe before unsubscribing: the overlap costs a duplicate message, which the
        // pushKey dedup absorbs, whereas the reverse order opens a window with no coverage.
        (desired - current).forEach { topic ->
            runCatching { messaging.subscribeToTopic(topic).awaitCompletion() }
                .onSuccess { current += topic }
                .onFailure { Log.w(TAG, "Failed to subscribe to $topic; retrying next sync", it) }
        }
        stale.forEach { topic ->
            runCatching { messaging.unsubscribeFromTopic(topic).awaitCompletion() }
                .onSuccess { current -= topic }
                .onFailure {
                    Log.w(TAG, "Failed to unsubscribe from $topic; retrying next sync", it)
                    current += topic
                }
        }

        prefs(context).edit().putStringSet(APPLIED_TOPICS_KEY, current).apply()
        Log.d(TAG, "FCM topics now ${current.sorted()} (wanted ${desired.sorted()})")
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Awaits a Play Services [Task]. Hand-rolled rather than pulling in
     * kotlinx-coroutines-play-services for the two calls in this file.
     */
    private suspend fun <T> Task<T>.awaitCompletion(): T = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            val error = task.exception
            when {
                error != null -> continuation.resumeWith(Result.failure(error))
                task.isCanceled -> continuation.cancel()
                else -> continuation.resume(task.result)
            }
        }
    }
}
