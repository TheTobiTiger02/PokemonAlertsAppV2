package com.example.pokemonalertsv2.data.dextracker

import android.annotation.SuppressLint
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.pokemonalertsv2.BuildConfig
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Undoes one quick edit: [before] → [after] of an entry, logged as [event]. */
data class DexTrackerUndo(
    val collectionId: String,
    val before: DexTrackerEntry,
    val after: DexTrackerEntry,
    val event: DexTrackerEvent?
)

/**
 * Dex Tracker (the PokemonGoChecklist web app) as a checklist source. It has its own Firebase
 * project, used here as a second Firebase app named "dextracker": Google sign-in to it, live
 * collections, entries and alt accounts from its Firestore, and the web app's quick edits written
 * the way it writes them (store.ts quickEdit: increments, the day's counter and an activity
 * event in one batch). Firestore's offline cache queues writes made without a connection.
 */
class DexTrackerRepository private constructor(private val appContext: Context) {
    private val app: FirebaseApp? = runCatching {
        if (!isConfigured) return@runCatching null
        FirebaseApp.getApps(appContext).firstOrNull { it.name == APP_NAME }
            ?: FirebaseApp.initializeApp(
                appContext,
                FirebaseOptions.Builder()
                    .setProjectId(BuildConfig.DEX_TRACKER_PROJECT_ID)
                    .setApplicationId(BuildConfig.DEX_TRACKER_APP_ID)
                    .setApiKey(BuildConfig.DEX_TRACKER_API_KEY)
                    .build(),
                APP_NAME
            )
    }.getOrNull()

    private val auth: FirebaseAuth? = app?.let { FirebaseAuth.getInstance(it) }
    private val firestore: FirebaseFirestore? = app?.let { FirebaseFirestore.getInstance(it) }

    private val _uid = MutableStateFlow(auth?.currentUser?.uid)

    /** The signed-in Dex Tracker user, or null. */
    val uid: StateFlow<String?> = _uid.asStateFlow()

    init {
        auth?.addAuthStateListener { _uid.value = it.currentUser?.uid }
    }

    val available: Boolean get() = app != null

    /** Google sign-in to Dex Tracker's project. Returns false when the user closed the picker. */
    @SuppressLint("CredentialManagerSignInWithGoogle") // Both credential types are handled below.
    suspend fun signIn(activityContext: Context): Boolean {
        val auth = auth ?: error("Dex Tracker is not set up in this build")
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(BuildConfig.DEX_TRACKER_WEB_CLIENT_ID).build())
            .build()
        val credential = try {
            CredentialManager.create(activityContext).getCredential(activityContext, request).credential
        } catch (cancelled: GetCredentialCancellationException) {
            return false
        } catch (none: NoCredentialException) {
            error("No Google account on this device")
        }
        val types = setOf(GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL, GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL)
        check(credential is CustomCredential && credential.type in types) { "Unexpected credential type" }
        val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
        await(auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)))
        return true
    }

    suspend fun signOut(context: Context) {
        auth?.signOut()
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    fun collections(uid: String): Flow<List<DexTrackerCollection>> =
        listen("users/$uid/collections") { docs -> docs.mapNotNull(::toCollection).sortedBy { it.createdAt } }

    fun accounts(uid: String): Flow<List<DexTrackerAccount>> =
        listen("users/$uid/accounts") { docs -> docs.mapNotNull(::toAccount).sortedBy { it.order } }

    /**
     * The entries of a collection. The latest snapshot is also kept for [apply] and [undo], so
     * an action always starts from the entry as it is now, not as a screen last drew it.
     */
    fun entries(uid: String, collectionId: String): Flow<List<DexTrackerEntry>> =
        listen("users/$uid/collections/$collectionId/entries") { docs ->
            docs.mapNotNull(::toEntry).sortedBy { it.order }.also { list -> latest[collectionId] = list.associateBy { it.id } }
        }

    /** The last few activity events of one entry, newest first. */
    fun events(uid: String, collectionId: String, entryId: String, limit: Int = 5): Flow<List<DexTrackerEvent>> {
        val db = firestore ?: return emptyFlow()
        return callbackFlow {
            // Sorted here: an equality filter with an order on another field needs an index.
            val registration = db.collection("users/$uid/collections/$collectionId/events")
                .whereEqualTo("entryId", entryId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    trySend(snapshot?.documents.orEmpty().mapNotNull(::toEvent).sortedByDescending { it.at }.take(limit))
                }
            awaitClose { registration.remove() }
        }
    }

    private fun <T> listen(path: String, map: (List<DocumentSnapshot>) -> T): Flow<T> {
        val db = firestore ?: return emptyFlow()
        return callbackFlow {
            val registration = db.collection(path).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(map(snapshot?.documents.orEmpty()))
            }
            awaitClose { registration.remove() }
        }
    }

    /**
     * Badge entries ([DexTrackerAlertIndex]) of the given collections, keyed by collection id,
     * while signed in. A listener that fails (offline, permissions) tries again later.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun alertEntries(collectionIds: Set<String>): Flow<Map<String, List<com.example.pokemonalertsv2.data.database.GoDexEntryEntity>>> {
        if (firestore == null || collectionIds.isEmpty()) return flowOf(emptyMap())
        val catalog = DexTrackerCatalog.get(appContext)
        return uid.flatMapLatest { user ->
            if (user == null) return@flatMapLatest flowOf(emptyMap())
            combine(
                collectionIds.map { id ->
                    entries(user, id)
                        .retryWhen { _, attempt ->
                            delay(minOf(RETRY_MAX_MILLIS, RETRY_BASE_MILLIS shl attempt.toInt().coerceAtMost(4)))
                            true
                        }
                        .map { id to DexTrackerAlertIndex.entries(it, catalog) }
                }
            ) { pairs -> pairs.toMap() }
        }
    }

    private val latest = ConcurrentHashMap<String, Map<String, DexTrackerEntry>>()

    private fun current(collectionId: String, entryId: String): DexTrackerEntry =
        latest[collectionId]?.get(entryId) ?: throw DexTrackerException("This entry was removed.")

    private fun remember(collectionId: String, entry: DexTrackerEntry) {
        latest.compute(collectionId) { _, entries -> entries.orEmpty() + (entry.id to entry) }
    }

    /**
     * One quick edit, as the web app's quickEdit: atomic increments on the entry, the day's
     * credited count and an activity event, in one batch. Not awaited: offline, the write waits in
     * Firestore's queue and the snapshot shows it at once. Throws [DexTrackerException] with the
     * web app's message when the action does not fit the entry.
     */
    fun apply(collectionId: String, entryId: String, action: DexTrackerAction): DexTrackerUndo {
        val before = current(collectionId, entryId)
        val patch = DexTrackerDomain.actionPatch(before, action)
        val event = DexTrackerDomain.describeChange(before, patch.next, System.currentTimeMillis(), UUID.randomUUID().toString())
        write(collectionId, entryId, patch, addEvent = event, removeEvent = null)
        remember(collectionId, patch.next)
        return DexTrackerUndo(collectionId, before, patch.next, event)
    }

    /** Takes a quick edit back on the entry as it is now, and drops its activity event. */
    fun undo(undo: DexTrackerUndo) {
        val now = current(undo.collectionId, undo.after.id)
        val patch = DexTrackerDomain.revertPatch(now, undo.before, undo.after)
        write(undo.collectionId, undo.after.id, patch, addEvent = null, removeEvent = undo.event?.id)
        remember(undo.collectionId, patch.next)
    }

    private fun write(collectionId: String, entryId: String, patch: DexTrackerPatch, addEvent: DexTrackerEvent?, removeEvent: String?) {
        val db = firestore ?: throw DexTrackerException("Dex Tracker is not set up")
        val uid = _uid.value ?: throw DexTrackerException("Not signed in to Dex Tracker")
        val collection = db.document("users/$uid/collections/$collectionId")
        val batch = db.batch()
        batch.update(collection.collection("entries").document(entryId), patch.increments.mapValues { FieldValue.increment(it.value) })
        if (patch.gained != 0) {
            val day = DexTrackerDomain.dayKey()
            batch.set(
                collection.collection("activity").document(day),
                mapOf("date" to day, "gained" to FieldValue.increment(patch.gained.toLong())),
                SetOptions.merge()
            )
        }
        addEvent?.let { batch.set(collection.collection("events").document(it.id), it.toMap()) }
        removeEvent?.let { batch.delete(collection.collection("events").document(it)) }
        batch.commit()
    }

    private fun toCollection(doc: DocumentSnapshot): DexTrackerCollection? = runCatching {
        DexTrackerCollection(
            id = doc.id,
            title = doc.getString("title") ?: return null,
            archived = doc.getBoolean("archived") ?: false,
            createdAt = doc.getDouble("createdAt") ?: 0.0
        )
    }.getOrNull()

    private fun toAccount(doc: DocumentSnapshot): DexTrackerAccount? = runCatching {
        DexTrackerAccount(doc.id, doc.getString("name") ?: return null, doc.getLong("order")?.toInt() ?: 0)
    }.getOrNull()

    /** Counts per key; zero counts are dropped, as the web app's own reads treat them. */
    private fun counts(value: Any?): Map<String, Int> =
        (value as? Map<*, *>).orEmpty().entries
            .mapNotNull { (key, count) -> (key as? String)?.let { it to ((count as? Number)?.toInt() ?: 0) } }
            .filter { it.second > 0 }
            .toMap()

    private fun toEntry(doc: DocumentSnapshot): DexTrackerEntry? = runCatching {
        DexTrackerEntry(
            id = doc.id,
            pokemonId = doc.getLong("pokemonId")?.toInt() ?: return null,
            name = doc.getString("name") ?: return null,
            label = doc.getString("label").orEmpty(),
            target = doc.getLong("target")?.toInt() ?: 1,
            targets = (doc.get("targets") as? List<*>).orEmpty().mapNotNull { (it as? Number)?.toInt() },
            owned = counts(doc.get("owned")),
            unassigned = doc.getLong("unassigned")?.toInt() ?: 0,
            // Entries saved before alt accounts existed have no alts map.
            extras = doc.getLong("extras")?.toInt() ?: 0,
            alts = counts(doc.get("alts")),
            order = doc.getLong("order")?.toInt() ?: 0,
            revision = doc.getLong("revision") ?: 0
        )
    }.getOrNull()

    private fun toEvent(doc: DocumentSnapshot): DexTrackerEvent? = runCatching {
        DexTrackerEvent(
            id = doc.id,
            at = doc.getLong("at") ?: return null,
            entryId = doc.getString("entryId") ?: return null,
            entryName = doc.getString("entryName").orEmpty(),
            pokemonId = doc.getLong("pokemonId")?.toInt() ?: return null,
            kind = doc.getString("kind") ?: return null,
            count = doc.getLong("count")?.toInt() ?: 0,
            change = doc.getLong("change")?.toInt() ?: 0,
            account = if (doc.contains("account")) DexTrackerPool(doc.getString("account")) else null
        )
    }.getOrNull()

    private suspend fun <T> await(task: Task<T>): T = withContext(Dispatchers.IO) { Tasks.await(task) }

    companion object {
        private const val APP_NAME = "dextracker"
        private const val RETRY_BASE_MILLIS = 5_000L
        private const val RETRY_MAX_MILLIS = 60_000L

        /** Whether this build has Dex Tracker's Firebase settings (local.properties DEX_TRACKER_*). */
        val isConfigured: Boolean
            get() = listOf(
                BuildConfig.DEX_TRACKER_PROJECT_ID, BuildConfig.DEX_TRACKER_APP_ID,
                BuildConfig.DEX_TRACKER_API_KEY, BuildConfig.DEX_TRACKER_WEB_CLIENT_ID
            ).all { it.isNotBlank() }

        /** PokéAPI sprite by Pokémon id (Dex Tracker's own ids, alternate forms above 10000 included). */
        fun spriteUrl(pokemonId: Int) = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/$pokemonId.png"

        @Volatile private var instance: DexTrackerRepository? = null

        fun getInstance(context: Context): DexTrackerRepository = instance ?: synchronized(this) {
            instance ?: DexTrackerRepository(context.applicationContext).also { instance = it }
        }
    }
}
