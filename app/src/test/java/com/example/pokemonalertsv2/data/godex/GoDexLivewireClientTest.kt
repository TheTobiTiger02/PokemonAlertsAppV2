package com.example.pokemonalertsv2.data.godex

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class GoDexLivewireClientTest {
    @Test
    fun alreadyAppliedStateDoesNotToggle() = runTest {
        val postCount = AtomicInteger()
        val client = GoDexLivewireClient(fakeClient(initiallyCaught = true, postCount))

        val result = client.setCaught(
            url = COLLECTION_URL,
            cookies = "PHPSESSID=session",
            entryKey = ENTRY_KEY,
            caught = true
        )

        assertTrue(result is GoDexWriteResult.AlreadyApplied)
        assertEquals(0, postCount.get())
    }

    @Test
    fun requestedStateIsVerifiedBeforeSuccess() = runTest {
        val postCount = AtomicInteger()
        val client = GoDexLivewireClient(fakeClient(initiallyCaught = false, postCount))

        val result = client.setCaught(
            url = COLLECTION_URL,
            cookies = "PHPSESSID=session",
            entryKey = ENTRY_KEY,
            caught = true
        )

        assertTrue(result is GoDexWriteResult.Applied)
        assertEquals(1, postCount.get())
    }

    @Test
    fun sessionExpiryReturnsTypedOutcome() = runTest {
        val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                response(chain, 419, "expired")
            }
            .build()

        val result = GoDexLivewireClient(httpClient).setCaught(
            url = COLLECTION_URL,
            cookies = "PHPSESSID=session",
            entryKey = ENTRY_KEY,
            caught = true
        )

        assertTrue(result is GoDexWriteResult.ReauthenticationRequired)
    }

    private fun fakeClient(initiallyCaught: Boolean, postCount: AtomicInteger): OkHttpClient {
        val snapshotBefore = snapshot(initiallyCaught)
        val snapshotAfter = snapshot(true)
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                if (chain.request().method == "GET") {
                    val html = """
                        <html>
                          <head><meta name="csrf-token" content="csrf"></head>
                          <body>
                            <div wire:snapshot='$snapshotBefore'>
                              <button wire:key="$ENTRY_KEY" wire:click="toggle('$ENTRY_KEY')">Pikachu</button>
                            </div>
                          </body>
                        </html>
                    """.trimIndent()
                    response(chain, 200, html)
                } else {
                    postCount.incrementAndGet()
                    val body = buildJsonObject {
                        put("components", buildJsonArray {
                            add(buildJsonObject {
                                put("snapshot", JsonPrimitive(snapshotAfter))
                                put("effects", buildJsonObject {})
                            })
                        })
                    }.toString()
                    response(chain, 200, body)
                }
            }
            .build()
    }

    private fun snapshot(caught: Boolean): String = buildJsonObject {
        put("data", buildJsonObject {
            put("pokemonsCaught", buildJsonArray {
                add(buildJsonObject {
                    put("none", buildJsonArray {
                        add(buildJsonObject {
                            if (caught) put("0025", true)
                        })
                        add(buildJsonObject {})
                    })
                })
            })
        })
    }.toString()

    private fun response(chain: Interceptor.Chain, code: Int, body: String): Response =
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 200) "OK" else "Error")
            .body(body.toResponseBody())
            .build()

    private companion object {
        const val COLLECTION_URL = "https://godex.site/collection/test"
        const val ENTRY_KEY = "0025-none"
    }
}
