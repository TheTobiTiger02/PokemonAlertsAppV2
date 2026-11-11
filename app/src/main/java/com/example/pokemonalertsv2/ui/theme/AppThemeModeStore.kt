package com.example.pokemonalertsv2.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pokemonalertsv2.data.AlertPreferences
import com.example.pokemonalertsv2.data.alertPreferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The stored theme mode, read once per process and kept current.
 *
 * Every screen used to start its own DataStore read with "System" as the initial value, so a
 * user who picked Light or Dark saw each newly opened screen flash in the system theme first.
 * Started from the Application; a screen opened later gets the cached value on its first frame.
 */
object AppThemeModeStore {
    private val _mode = MutableStateFlow<Int?>(null)
    val mode: StateFlow<Int?> = _mode.asStateFlow()
    private val started = AtomicBoolean(false)

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val preferences = AlertPreferences(context.applicationContext.alertPreferencesDataStore)
        // Lives as long as the process, like the DataStore it mirrors.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            preferences.themeMode.collect { _mode.value = it }
        }
    }
}

/** Whether the app should draw dark right now: the user's choice, or the system's for "System". */
@Composable
fun rememberAppDarkTheme(): Boolean {
    val context = LocalContext.current
    // The Application starts it; this covers tests and previews that skip the Application.
    LaunchedEffect(Unit) { AppThemeModeStore.start(context) }
    val mode by AppThemeModeStore.mode.collectAsStateWithLifecycle()
    return AppThemeMode.fromStored(mode ?: 0).resolveDark(isSystemInDarkTheme())
}
