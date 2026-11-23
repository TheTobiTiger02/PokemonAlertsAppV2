package com.example.pokemonalertsv2.account

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.pokemonalertsv2.BuildConfig
import com.example.pokemonalertsv2.data.PokemonAlertsApi
import com.example.pokemonalertsv2.sync.DeviceSync
import com.example.pokemonalertsv2.sync.DeviceSyncEngine
import com.example.pokemonalertsv2.sync.SyncAccountException
import com.example.pokemonalertsv2.sync.SyncCrypto
import com.example.pokemonalertsv2.sync.SyncGroup
import com.example.pokemonalertsv2.sync.SyncStateStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Base64

/**
 * Links this device's sync to the signed-in account (DEVICE_SYNC.md, "Accounts"): the server
 * keeps the account's sync secret, so signing in on a device joins the account's group.
 *
 * The first device to sign in links its own group (or a new one); every later device joins
 * that group with the usual join rule (the group's values win, items only this device has are
 * kept). A device in another group leaves it first.
 */
class AccountLinker(
    private val service: AccountService,
    private val deviceId: String,
    private val engine: DeviceSyncEngine,
    private val state: SyncStateStore,
    private val registration: () -> DeviceRegistration
) {
    enum class Outcome { CREATED, JOINED, ALREADY_LINKED }

    suspend fun turnOn(): Outcome {
        service.registerSelf(deviceId, registration()).orThrow()
        val current = state.group()
        val offered = current ?: SyncCrypto.newGroup()
        val attached = service.attach(deviceId, SecretDto(SyncCrypto.base64Url(offered.secret))).bodyOrThrow()
        val secret = Base64.getUrlDecoder().decode(attached.secret)
        val group = SyncGroup(secret)
        return when {
            current != null && current.groupId == group.groupId -> {
                // A network failure here is the worker's to retry: the link itself is done.
                runCatching { engine.syncNow() }
                Outcome.ALREADY_LINKED
            }
            current == null && offered.groupId == group.groupId -> {
                engine.create(group)
                Outcome.CREATED
            }
            else -> {
                if (current != null) engine.leave()
                engine.join(secret)
                Outcome.JOINED
            }
        }
    }
}

/** The app's side of accounts: sign-in, the device list, sign-out and account deletion. */
object AccountSync {
    private const val TAG = "AccountSync"

    /** Shown on the sync page after another device removed this one from the account. */
    const val NOTICE_REMOVED = "removed_from_account"

    private val _devices = MutableStateFlow<List<AccountDeviceDto>>(emptyList())

    /** The account's signed-in devices, as the server last listed them. */
    val devices: StateFlow<List<AccountDeviceDto>> = _devices.asStateFlow()

    private val _paused = MutableStateFlow(false)

    /** The group is linked to an account and this device is not signed in to it. */
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    private fun linker(app: Context) = AccountLinker(
        service = PokemonAlertsApi.accountService,
        deviceId = DeviceSync.deviceId(app),
        engine = DeviceSync.engine(app),
        state = DeviceSync.state(app),
        registration = {
            DeviceRegistration(
                name = Build.MODEL.orEmpty(),
                model = listOf(Build.MANUFACTURER, Build.MODEL).filterNot { it.isNullOrBlank() }.joinToString(" "),
                appVersion = BuildConfig.VERSION_CODE.toLong()
            )
        }
    )

    /** Google sign-in, then sync on with the account's group. Null when the user closed the picker. */
    suspend fun signIn(activity: Context): AccountLinker.Outcome? {
        AccountRepository.signIn(activity) ?: return null
        return turnOn(activity.applicationContext)
    }

    /** Signed in: links or joins the account's group (also "Turn sync on" after it was deleted). */
    suspend fun turnOn(context: Context): AccountLinker.Outcome {
        val app = context.applicationContext
        val outcome = linker(app).turnOn()
        _paused.value = false
        refresh(app)
        return outcome
    }

    /** Reloads the account's devices; learns here when another device removed this one. */
    suspend fun refresh(context: Context) {
        val app = context.applicationContext
        if (AccountRepository.account.value == null) {
            _devices.value = emptyList()
            return
        }
        try {
            _devices.value = PokemonAlertsApi.accountService.account(DeviceSync.deviceId(app)).bodyOrThrow().devices
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: AccountApiException) {
            if (error.code == SyncAccountException.DEVICE_REVOKED) onRemoved(app)
            else Log.w(TAG, "Could not load the account", error)
        } catch (error: Exception) {
            Log.w(TAG, "Could not load the account", error)
        }
    }

    suspend fun removeDevice(context: Context, deviceId: String) {
        val app = context.applicationContext
        PokemonAlertsApi.accountService.removeDevice(deviceId, DeviceSync.deviceId(app)).orThrow()
        refresh(app)
    }

    /** Stops syncing here and signs out. Local data stays; the other devices keep syncing. */
    suspend fun signOut(context: Context) {
        val app = context.applicationContext
        // Leave first: its last sync request would register this device on the account again.
        DeviceSync.engine(app).leave()
        runCatching { PokemonAlertsApi.accountService.removeDevice(DeviceSync.deviceId(app), DeviceSync.deviceId(app)).orThrow() }
            .onFailure { Log.w(TAG, "Could not sign this device out on the server", it) }
        AccountRepository.signOut(app)
        _devices.value = emptyList()
        _paused.value = false
    }

    /** Deletes the synced data, the account and the Google sign-in. Each device keeps its own data. */
    suspend fun deleteAccount(context: Context) {
        val app = context.applicationContext
        DeviceSync.engine(app).deleteEverywhere()
        PokemonAlertsApi.accountService.delete(DeviceSync.deviceId(app)).orThrow()
        AccountRepository.signOut(app)
        _devices.value = emptyList()
        _paused.value = false
    }

    /** The server refused a sync request for the account (DeviceSyncEngine's onAccountRejected). */
    suspend fun onSyncRejected(context: Context, rejection: SyncAccountException) {
        if (rejection.revoked) onRemoved(context.applicationContext) else _paused.value = true
    }

    private suspend fun onRemoved(app: Context) {
        DeviceSync.engine(app).leave(notice = NOTICE_REMOVED)
        AccountRepository.signOut(app)
        _devices.value = emptyList()
        _paused.value = false
    }
}
