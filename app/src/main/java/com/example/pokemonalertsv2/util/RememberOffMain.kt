package com.example.pokemonalertsv2.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Like `remember(keys) { compute() }`, but recomputes on [Dispatchers.Default].
 *
 * For list work over the whole feed (filtering and sorting ~1500 alerts) that used to block the
 * main thread inside composition on every coarse tick. The first value is computed synchronously
 * so the screen never flashes empty; afterwards the previous result stays on screen until the new
 * one is ready, normally a frame or two later. [compute] must only read its inputs, which must be
 * immutable, because it runs on a worker thread.
 */
@Composable
fun <T> rememberOffMain(vararg keys: Any?, compute: () -> T): T {
    val holder = remember { OffMainHolder<T>() }
    if (!holder.initialized) {
        holder.value = compute()
        holder.keys = keys.toList()
        holder.initialized = true
    }
    LaunchedEffect(*keys) {
        val requested = keys.toList()
        // The synchronous first value already used these exact inputs. Identity, not equals:
        // deep-comparing a 1500-alert list on the main thread is the cost this avoids.
        if (requested.size == holder.keys.size && requested.indices.all { requested[it] === holder.keys[it] }) {
            return@LaunchedEffect
        }
        val next = withContext(Dispatchers.Default) { compute() }
        holder.keys = requested
        holder.value = next
    }
    @Suppress("UNCHECKED_CAST")
    return holder.value as T
}

private class OffMainHolder<T> {
    var initialized = false
    var keys: List<Any?> = emptyList()
    var value: Any? by mutableStateOf(null)
}
