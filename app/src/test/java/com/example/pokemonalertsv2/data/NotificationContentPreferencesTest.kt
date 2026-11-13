package com.example.pokemonalertsv2.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.example.pokemonalertsv2.data.backup.SettingsBackup
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class NotificationContentPreferencesTest {
    @Test
    fun pictureAndTitleDefaultPerCategoryAndRoundTrip() {
        val defaults = NotificationContentPreferences()
        assertEquals(NotificationImageStyle.LARGE, defaults.imageStyle(FilterAlertType.RAID))
        assertEquals(
            setOf(NotificationTitlePart.GODEX_MARKER, NotificationTitlePart.CATEGORY, NotificationTitlePart.SUBJECT, NotificationTitlePart.CP),
            defaults.titleParts(FilterAlertType.HUNDO)
        )
        assertEquals(setOf(NotificationTitlePart.CATEGORY, NotificationTitlePart.SUBJECT), defaults.titleParts(FilterAlertType.QUEST))
        assertFalse(defaults.isCustomized(FilterAlertType.RAID))

        val changed = defaults
            .withImageStyle(FilterAlertType.RAID, NotificationImageStyle.NONE)
            .withTitlePart(FilterAlertType.RAID, NotificationTitlePart.VENUE, true)
            .withTitlePart(FilterAlertType.QUEST, NotificationTitlePart.IV, true)
        assertEquals(NotificationImageStyle.NONE, changed.imageStyle(FilterAlertType.RAID))
        assertTrue(NotificationTitlePart.VENUE in changed.titleParts(FilterAlertType.RAID))
        assertFalse(NotificationTitlePart.IV in changed.titleParts(FilterAlertType.QUEST))
        assertTrue(changed.isCustomized(FilterAlertType.RAID))
        assertEquals(changed, NotificationContentCodec.decode(NotificationContentCodec.encode(changed)))
        assertEquals(defaults, changed.reset(FilterAlertType.RAID).reset(FilterAlertType.QUEST))
    }

    @Test
    fun defaultsKeepCollapsedShortAndTheExpandedSummaryFocused() {
        val defaults = NotificationContentPreferences()
        val hundo = defaults.fields(FilterAlertType.HUNDO, false)
        assertTrue(NotificationField.IV in hundo)
        assertFalse(NotificationField.WALKING_TIME in hundo)
        assertTrue(NotificationField.VENUE in defaults.fields(FilterAlertType.RAID, false))
        assertTrue(NotificationField.VENUE in defaults.fields(FilterAlertType.QUEST, false))
        assertTrue(NotificationField.WEATHER in defaults.fields(FilterAlertType.RAID, true))
        assertFalse(NotificationField.WEATHER in defaults.fields(FilterAlertType.HUNDO, true))
        assertFalse(NotificationField.AREA in defaults.fields(FilterAlertType.RAID, true))
        assertTrue(NotificationField.WALKING_TIME in defaults.fields(FilterAlertType.HUNDO, true))
    }

    @Test
    fun storedChoicesFromBeforePicturesAndTitlesKeepWorking() {
        val decoded = NotificationContentCodec.decode("""{"version":1,"categories":{"RAID":{"collapsed":["WEATHER"]}}}""")
        assertEquals(NotificationImageStyle.LARGE, decoded.imageStyle(FilterAlertType.RAID))
        assertEquals(setOf(NotificationTitlePart.CATEGORY, NotificationTitlePart.SUBJECT), decoded.titleParts(FilterAlertType.RAID))
        val unknown = NotificationContentCodec.decode("""{"categories":{"RAID":{"image":"HOLOGRAM"}}}""")
        assertEquals(NotificationImageStyle.LARGE, unknown.imageStyle(FilterAlertType.RAID))
    }

    @Test
    fun categoryAndDetailLevelAreIndependentAndResetRestoresDefaults() {
        val defaults = NotificationContentPreferences()
        val changed = defaults.withField(FilterAlertType.RAID, false, NotificationField.WEATHER, false)
        assertFalse(NotificationField.WEATHER in changed.fields(FilterAlertType.RAID, false))
        assertTrue(NotificationField.WEATHER in changed.fields(FilterAlertType.RAID, true))
        assertEquals(defaults.fields(FilterAlertType.HUNDO, false), changed.fields(FilterAlertType.HUNDO, false))
        assertEquals(defaults, changed.reset(FilterAlertType.RAID))
    }

    @Test
    fun unknownNamesAreToleratedAndSurviveAnUnrelatedFieldEdit() {
        val decoded = NotificationContentCodec.decode("""{"version":1,"newProperty":true,"categories":{"RAID":{"collapsed":["WEATHER","FUTURE_FIELD"]},"FUTURE_CATEGORY":{"collapsed":[]}}}""")
        assertEquals(setOf(NotificationField.WEATHER), decoded.fields(FilterAlertType.RAID, false))
        val edited = decoded.withField(FilterAlertType.RAID, false, NotificationField.TIME_LEFT, true)
        assertTrue("FUTURE_FIELD" in edited.categories.getValue("RAID").collapsed!!)
        assertTrue("FUTURE_CATEGORY" in edited.categories)
        assertEquals(edited, NotificationContentCodec.decode(NotificationContentCodec.encode(edited)))
        assertEquals(NotificationContentPreferences(), NotificationContentCodec.decode("broken"))
    }

    @Test
    fun semanticCategoriesWinOverEncounterIvClassifications() {
        val perfectRaid = PokemonAlert(
            name = "Raid", type = listOf("Raid", "Hundo"),
            iv = "15/15/15", ivAttack = 15, ivDefense = 15, ivStamina = 15
        )
        assertEquals(FilterAlertType.RAID, notificationContentCategory(perfectRaid))
        assertEquals(FilterAlertType.QUEST, notificationContentCategory(perfectRaid.copy(type = listOf("Quest", "Hundo"))))
        assertEquals(FilterAlertType.HUNDO, notificationContentCategory(perfectRaid.copy(type = listOf("Spawn"))))
        assertEquals(FilterAlertType.OTHER, notificationContentCategory(PokemonAlert(name = "Other")))
    }

    @Test
    fun concurrentEditsPersistAndRoundTripThroughSettingsBackup() = runTest {
        val store = MemoryStore()
        val preferences = AlertPreferences(store)
        listOf(
            async { preferences.updateNotificationContent { it.withField(FilterAlertType.RAID, false, NotificationField.WEATHER, false) } },
            async { preferences.updateNotificationContent { it.withField(FilterAlertType.HUNDO, true, NotificationField.CP, false) } }
        ).awaitAll()
        val recreated = AlertPreferences(store).notificationContent.first()
        assertFalse(NotificationField.WEATHER in recreated.fields(FilterAlertType.RAID, false))
        assertFalse(NotificationField.CP in recreated.fields(FilterAlertType.HUNDO, true))
        val backup = SettingsBackup.parse(SettingsBackup.export(store.data.first(), 123L)).getOrThrow()
        val imported = emptyPreferences().toMutablePreferences()
        SettingsBackup.apply(backup, imported)
        assertEquals(recreated, NotificationContentCodec.decode(imported[NOTIFICATION_CONTENT_KEY]))
    }

    private class MemoryStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        private val mutex = Mutex()
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
            transform(state.value).also { state.value = it }
        }
    }
}
