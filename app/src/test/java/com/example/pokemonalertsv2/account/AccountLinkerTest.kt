package com.example.pokemonalertsv2.account

import com.example.pokemonalertsv2.sync.DeviceSync
import com.example.pokemonalertsv2.sync.DeviceSyncEngine
import com.example.pokemonalertsv2.sync.FakeDevice
import com.example.pokemonalertsv2.sync.FakeServer
import com.example.pokemonalertsv2.sync.FetchResult
import com.example.pokemonalertsv2.sync.MapAdapter
import com.example.pokemonalertsv2.sync.PutResult
import com.example.pokemonalertsv2.sync.SyncAccountException
import com.example.pokemonalertsv2.sync.SyncCrypto
import com.example.pokemonalertsv2.sync.SyncGroup
import com.example.pokemonalertsv2.sync.SyncStateStore
import com.example.pokemonalertsv2.sync.SyncTransport
import com.example.pokemonalertsv2.sync.PlainSealer
import com.example.pokemonalertsv2.sync.testDataStore
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Response

class AccountLinkerTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = FakeServer()

    /** The account routes in memory: the first link wins, as account.js does. */
    private inner class FakeAccounts : AccountService {
        var secret: String? = null
        val registered = mutableListOf<String>()
        var revoked = false

        override suspend fun account(device: String, appKey: String?): Response<AccountDto> =
            Response.success(AccountDto("uid", sync = secret?.let { SecretDto(it) }))

        override suspend fun registerSelf(device: String, body: DeviceRegistration, appKey: String?): Response<ResponseBody> {
            registered += device
            return Response.success("".toResponseBody())
        }

        override suspend fun attach(device: String, body: SecretDto, appKey: String?): Response<AttachDto> {
            if (revoked) return Response.error(403, """{"code":"DEVICE_REVOKED"}""".toResponseBody("application/json".toMediaType()))
            val existing = secret
            if (existing != null) return Response.success(AttachDto(existing, attached = false))
            secret = body.secret
            // Linking creates the group row on the server.
            server.groupId = SyncGroup(java.util.Base64.getUrlDecoder().decode(body.secret)).groupId
            return Response.success(AttachDto(body.secret, attached = true))
        }

        override suspend fun removeDevice(deviceId: String, device: String, appKey: String?) = Response.success("".toResponseBody())
        override suspend fun delete(device: String, appKey: String?) = Response.success("".toResponseBody())
    }

    private val accounts = FakeAccounts()

    private fun linker(device: FakeDevice) = AccountLinker(accounts, device.id, device.engine, device.state) {
        DeviceRegistration(device.id, "Model", 1)
    }

    @Test
    fun firstDeviceCreatesTheGroupAndTheNextJoinsIt() = runBlocking {
        val a = FakeDevice(folder.root, "A", server, now = 1_000)
        a.adapter.data += mapOf("theme" to "dark")
        assertEquals(AccountLinker.Outcome.CREATED, linker(a).turnOn())
        val group = a.state.group()!!
        assertEquals("the account holds this device's new group", SyncCrypto.base64Url(group.secret), accounts.secret)
        assertEquals("dark", server.document(group, DeviceSync.SETTINGS).entries.getValue("theme").value)

        val b = FakeDevice(folder.root, "B", server, now = 2_000)
        b.adapter.data += mapOf("theme" to "light", "onlyOnB" to "kept")
        assertEquals(AccountLinker.Outcome.JOINED, linker(b).turnOn())
        assertEquals(group.groupId, b.state.group()!!.groupId)
        assertEquals("the group's value wins", "dark", b.adapter.data["theme"])
        assertEquals("kept", b.adapter.data["onlyOnB"])
        assertEquals(listOf("A", "B"), accounts.registered)
    }

    @Test
    fun aPairedGroupMovesIntoTheAccountUnchanged() = runBlocking {
        val a = FakeDevice(folder.root, "A", server, now = 1_000)
        a.adapter.data += mapOf("area" to "Alsbach")
        val paired = a.engine.create()

        assertEquals(AccountLinker.Outcome.ALREADY_LINKED, linker(a).turnOn())
        assertEquals("the existing group is linked, not replaced", paired.groupId, a.state.group()!!.groupId)
        assertEquals(SyncCrypto.base64Url(paired.secret), accounts.secret)
    }

    @Test
    fun aDeviceInAnotherGroupLeavesItAndJoinsTheAccounts() = runBlocking {
        val a = FakeDevice(folder.root, "A", server, now = 1_000)
        a.adapter.data += mapOf("theme" to "dark")
        linker(a).turnOn()
        val accountGroup = a.state.group()!!

        val b = FakeDevice(folder.root, "B", server, now = 2_000)
        b.state.connect(SyncCrypto.newGroup(), emptyMap()) // paired elsewhere earlier
        b.adapter.data += mapOf("onlyOnB" to "kept")
        assertEquals(AccountLinker.Outcome.JOINED, linker(b).turnOn())
        assertEquals(accountGroup.groupId, b.state.group()!!.groupId)
        assertEquals("dark", b.adapter.data["theme"])
        assertEquals("kept", b.adapter.data["onlyOnB"])
    }

    @Test
    fun aRemovedDeviceCannotLink() = runBlocking {
        val a = FakeDevice(folder.root, "A", server, now = 1_000)
        accounts.revoked = true
        val error = runCatching { linker(a).turnOn() }.exceptionOrNull() as? AccountApiException
        assertEquals("DEVICE_REVOKED", error?.code)
        assertNull("nothing is set up", a.state.group())
    }

    @Test
    fun aRefusedSyncStopsTheCycleAndReportsIt() = runBlocking {
        val refusing = object : SyncTransport {
            override suspend fun list(group: SyncGroup): Map<String, Long> = throw SyncAccountException(SyncAccountException.SIGN_IN_REQUIRED)
            override suspend fun fetch(group: SyncGroup, collection: String, knownVersion: Long?): FetchResult = throw SyncAccountException(SyncAccountException.SIGN_IN_REQUIRED)
            override suspend fun put(group: SyncGroup, collection: String, sealed: ByteArray, expectedVersion: Long, deviceId: String): PutResult =
                throw SyncAccountException(SyncAccountException.DEVICE_REVOKED)
            override suspend fun delete(group: SyncGroup) = Unit
        }
        val reported = mutableListOf<String>()
        val state = SyncStateStore(testDataStore(folder.root, "state-refused"), PlainSealer)
        val engine = DeviceSyncEngine(
            refusing, state, listOf(MapAdapter(DeviceSync.SETTINGS, mapOf("theme" to "dark"))), "R", clock = { 1_000 },
            onAccountRejected = { reported += it.code }
        )
        state.connect(SyncCrypto.newGroup(), emptyMap())
        assertFalse("no retry: the account has to change first", engine.syncNow())
        assertFalse(engine.syncLocalChange(DeviceSync.SETTINGS))
        assertEquals(listOf(SyncAccountException.SIGN_IN_REQUIRED, SyncAccountException.DEVICE_REVOKED), reported)
        assertTrue("the group stays, so signing in resumes it", state.group() != null)
    }

    @Test
    fun accountRefusalsAreReadFromTheBody() {
        assertEquals(SyncAccountException.DEVICE_REVOKED, SyncAccountException.fromResponse(403, """{"code":"DEVICE_REVOKED"}""")?.code)
        assertEquals(SyncAccountException.SIGN_IN_REQUIRED, SyncAccountException.fromResponse(401, """{"code":"ACCOUNT_SIGN_IN_REQUIRED"}""")?.code)
        assertNull("a bad group token is not an account problem", SyncAccountException.fromResponse(403, """{"code":"SYNC_AUTH_INVALID"}"""))
        assertNull(SyncAccountException.fromResponse(500, """{"code":"DEVICE_REVOKED"}"""))
    }
}
