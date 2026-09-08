package com.example.pokemonalertsv2.ui.alerts

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MapArtworkConcurrencyInstrumentedTest {
    @Test fun cancelledOwnerDoesNotCancelVisibleWaiterAndMemoryHitReusesBitmap() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val server = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
        val workers = Executors.newCachedThreadPool()
        val received = CountDownLatch(1)
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        workers.submit {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                workers.submit {
                    runCatching { socket.use {
                        val reader = it.getInputStream().bufferedReader()
                        while (!reader.readLine().isNullOrEmpty()) { }
                        received.countDown()
                        Thread.sleep(250)
                        it.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            write(bytes)
                            flush()
                        }
                    } }
                }
            }
        }
        try {
            val url = "http://127.0.0.1:${server.localPort}/cancel-owner.png"
            val owner = async(Dispatchers.Default) { loadMapMarkerArtwork(context, url, 96) }
            assertTrue("The owner must reach the controlled server", withContext(Dispatchers.IO) {
                received.await(5, TimeUnit.SECONDS)
            })
            val waiter = async(Dispatchers.Default) { loadMapMarkerArtwork(context, url, 96) }
            delay(30)
            owner.cancelAndJoin()
            val result = withTimeout(10_000) { waiter.await() }
            assertNotNull("A visible marker must retry after its previous owner is cancelled", result)
            assertSame(result, loadMapMarkerArtwork(context, url, 96))
        } finally {
            server.close()
            workers.shutdownNow()
        }
    }
}
