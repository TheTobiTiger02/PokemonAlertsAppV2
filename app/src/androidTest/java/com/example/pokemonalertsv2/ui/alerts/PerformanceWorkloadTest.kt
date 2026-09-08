package com.example.pokemonalertsv2.ui.alerts

import android.graphics.Bitmap
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pokemonalertsv2.PokemonAlertsApplication
import com.example.pokemonalertsv2.data.*
import com.example.pokemonalertsv2.data.database.toEntity
import com.example.pokemonalertsv2.data.database.toDomain
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Deterministic workloads; writes results only to app-specific test output, never to the alert DB. */
class PerformanceWorkloadTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val label = InstrumentationRegistry.getArguments().getString("performanceLabel", "local")

    private fun record(name: String, samples: List<Double>, extra: String = "") {
        val sorted = samples.sorted()
        val line = "$label,$name,${sorted[sorted.size / 2]},${sorted.last()},${Debug.getPss()},$extra\n"
        Log.i("PerformanceWorkload", line.trim())
        File(context.getExternalFilesDir(null), "performance-$label.csv").appendText(line)
    }

    @Test fun mapPreparationAcrossDensities() = runBlocking {
        for (count in listOf(0, 200, 1000, 3000, 10000)) {
            val alerts = List(count) { index -> PokemonAlert(id = index + 1, name = "Fixture $index",
                type = listOf("Spawn"), latitude = 49.87 + index % 100 * 0.00003,
                longitude = 8.65 + index / 100 * 0.00003, endTime = "2099-01-01T00:00:00Z") }
            for (zoom in listOf(10.0, 14.0, 20.0)) {
                repeat(2) { prepareMapMarkers(alerts, null, zoom, null, emptySet(), MapClusteringPreset.CURRENT.config) }
                val samples = List(7) {
                    val start = SystemClock.elapsedRealtimeNanos()
                    val result = prepareMapMarkers(alerts, null, zoom, null, emptySet(), MapClusteringPreset.CURRENT.config)
                    assertEquals(count, result.alerts.size)
                    (SystemClock.elapsedRealtimeNanos() - start) / 1e6
                }
                record("map-$count-z$zoom", samples)
            }
            val entities = alerts.map { it.toEntity() }
            val decode = List(7) {
                val start = SystemClock.elapsedRealtimeNanos()
                assertEquals(count, entities.map { it.toDomain() }.size)
                (SystemClock.elapsedRealtimeNanos() - start) / 1e6
            }
            record("database-decode-$count", decode)
        }
    }

    @Test fun markerImagesColdDiskWarmDiskAndWarmMemory() = runBlocking {
        val server = ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"))
        val requests = AtomicInteger()
        val workers = Executors.newCachedThreadPool()
        val buffer = ByteArrayOutputStream()
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.MAGENTA)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, buffer)
        bitmap.recycle()
        val bytes = buffer.toByteArray()
        workers.submit {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                workers.submit {
                    socket.use {
                        val reader = it.getInputStream().bufferedReader()
                        while (!reader.readLine().isNullOrEmpty()) { }
                        requests.incrementAndGet()
                        Thread.sleep(30) // fixed service latency, excluded from live-network claims
                        val output = it.getOutputStream()
                        output.write("HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nCache-Control: max-age=3600\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        output.write(bytes)
                        output.flush()
                    }
                }
            }
        }
        try {
            val loader = PokemonAlertsApplication.imageLoader(context)
            for (cache in listOf("cold-disk", "warm-disk", "warm-memory")) {
                val samples = mutableListOf<Double>()
                val before = requests.get()
                repeat(7) { iteration ->
                    val urls = List(8) { "http://127.0.0.1:${server.localPort}/$cache-$iteration-$it.png" }
                    if (cache != "cold-disk") urls.forEach { assertNotNull(loadMapMarkerArtwork(context, it, 96)) }
                    if (cache != "warm-memory") { markerArtworkCache.evictAll(); loader.memoryCache?.clear() }
                    val start = SystemClock.elapsedRealtimeNanos()
                    coroutineScope {
                        List(128) { index -> async(Dispatchers.Default) {
                            assertNotNull(loadMapMarkerArtwork(context, urls[index % urls.size], 96))
                        } }.awaitAll()
                    }
                    samples += (SystemClock.elapsedRealtimeNanos() - start) / 1e6
                }
                record("images-$cache-128", samples, "requests=${requests.get() - before}")
            }
        } finally { server.close(); workers.shutdownNow() }
    }
}
