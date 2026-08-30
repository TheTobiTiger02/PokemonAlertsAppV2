package com.example.pokemonalertsv2.raidwatch

import androidx.activity.ComponentActivity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * The Live Update's "Copy team" action: writes the Pokémon GO search query and gets out of the
 * way. No UI of its own -- it is translucent, finishes immediately, and the trainer is back in
 * Pokémon GO with the query on the clipboard.
 *
 * An Activity rather than a broadcast into `NotificationActionReceiver` for two reasons: an app
 * in the background cannot reliably write the clipboard from Android 10 onward, and from
 * Android 12 a receiver may not launch an Activity to work around that. A notification action
 * pointing straight at an Activity is allowed, and collapses the shade for free.
 */
class CopyRaidTeamActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val store = RaidWatchStore(applicationContext)
            val stored = store.currentTeam()
            when {
                stored != null && stored.hasTeam -> copy(stored)
                stored != null -> toast(stored.note ?: NOTHING_TO_COPY)
                else -> copyAfterComputing()
            }
            finish()
        }
    }

    /**
     * The fallback for a tap that lands before the prefetch has finished -- an arrival with no
     * signal, or a watch restored after the worker was cancelled.
     */
    private suspend fun copyAfterComputing() {
        val alert = RaidWatchStore(applicationContext).current()?.alert ?: run {
            toast(NOTHING_TO_COPY)
            return
        }
        toast("Preparing your team…")
        val snapshot = RaidTeamPrefetcher.ensure(applicationContext, alert)
        when {
            snapshot == null -> toast("Couldn't reach Pokébattler. Try again in a moment.")
            snapshot.hasTeam -> {
                copy(snapshot)
                // The team is known now, so the notification can show it too.
                RaidWatchController.refresh(applicationContext)
            }
            else -> toast(snapshot.note ?: NOTHING_TO_COPY)
        }
    }

    private fun copy(snapshot: RaidTeamSnapshot) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(
            ClipData.newPlainText("Pokémon GO team search", snapshot.clipboardQuery)
        )
        when {
            // Never silent about the downgrade: the trainer asked for their exact six and is
            // getting every copy of those species instead, which they have to sort out in the
            // Pokemon list.
            snapshot.exactQueryTooLong ->
                toast("Copied species only — the exact query is too long for GO's search.")
            // Android 13+ shows its own copy confirmation; a second toast would stack on it.
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ->
                toast("Team copied for Pokémon GO")
        }
    }

    private fun toast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val NOTHING_TO_COPY = "No team to copy yet."
    }
}
