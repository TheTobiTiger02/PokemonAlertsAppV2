package com.example.pokemonalertsv2.widget

import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Where widget receivers run their goAsync() work. A bare CoroutineScope(Dispatchers.IO)
 * per broadcast let an exception escape to the default handler and kill the process;
 * here one failed update is logged and the next broadcast starts clean.
 */
internal val widgetWork = CoroutineScope(
    SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error ->
        Log.w("WidgetWork", "Widget update failed", error)
    }
)
