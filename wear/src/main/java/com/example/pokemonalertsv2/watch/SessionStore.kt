package com.example.pokemonalertsv2.watch

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/** The phone's running session as last heard, shared by the screen and the workout service. */
object SessionStore {
    private const val TAG = "SessionStore"
    private val mutable = MutableStateFlow(WearSessionState())
    val state: StateFlow<WearSessionState> = mutable.asStateFlow()

    /** Live heart rate for the screen; null until the sensor reports. */
    val heartRate = MutableStateFlow<Int?>(null)

    fun update(item: DataItem) {
        if (item.uri.path != WearProtocol.PATH_SESSION) return
        val text = DataMapItem.fromDataItem(item).dataMap.getString(WearProtocol.KEY_STATE)
        WearProtocol.decodeState(text)?.let { mutable.value = it }
    }

    fun clear() { mutable.value = WearSessionState() }

    /** Reads the current item, e.g. when the app opens after missing changes. */
    suspend fun refresh(context: Context) {
        runCatching {
            val items = Wearable.getDataClient(context).dataItems.await()
            try {
                items.forEach(::update)
            } finally {
                items.release()
            }
        }.onFailure { Log.w(TAG, "Could not read the phone's session", it) }
    }
}

/** Sends the watch's buttons and readings to the phone. */
object PhoneLink {
    private const val TAG = "PhoneLink"

    suspend fun send(context: Context, command: WearProtocol.Command) =
        sendToAll(context, WearProtocol.PATH_COMMAND, command.wire.toByteArray())

    suspend fun send(context: Context, sample: WatchSample) =
        sendToAll(context, WearProtocol.PATH_SAMPLE, WearProtocol.encodeSample(sample))

    private suspend fun sendToAll(context: Context, path: String, data: ByteArray): Boolean = runCatching {
        val nodes = Wearable.getNodeClient(context).connectedNodes.await()
        nodes.forEach { node -> Wearable.getMessageClient(context).sendMessage(node.id, path, data).await() }
        nodes.isNotEmpty()
    }.onFailure { Log.w(TAG, "Could not reach the phone", it) }.getOrDefault(false)
}
