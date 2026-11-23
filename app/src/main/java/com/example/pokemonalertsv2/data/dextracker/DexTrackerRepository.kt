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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import java.util.UUID

/** Undoes one "+1 caught". */
data class DexTrackerUndo(val collectionId: String, val entryId: String, val gained: Int, val eventId: String, val day: String)

/**
 * Dex Tracker (the PokemonGoChecklist web app) as a checklist source. It has its own Firebase
 * project, used here as a second Firebase app named "dextracker": Google sign-in to it, live
 * collections and entries from its Firestore, and "+1 caught" written the way the web app writes it.
 * Firestore's offline cache queues writes made without a connection.
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

    fun collections(uid: String): Flow<List<DexTrackerCollection>> {
        val db = firestore ?: return emptyFlow()
        return callbackFlow {
            val registration = db.collection("users/$uid/collections").addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents.orEmpty().mapNotNull(::toCollection).sortedBy { it.createdAt })
            }
            awaitClose { registration.remove() }
        }
    }

    fun entries(uid: String, collectionId: String): Flow<List<DexTrackerEntry>> {
        val db = firestore ?: return emptyFlow()
        return callbackFlow {
            val registration = db.collection("users/$uid/collections/$collectionId/entries").addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents.orEmpty().mapNotNull(::toEntry).sortedBy { it.order })
            }
            awaitClose { registration.remove() }
        }
    }

    /** One more caught copy on the main account, as an unassigned copy (the web app's quick +1). */
    suspend fun plusOne(collectionId: String, entry: DexTrackerEntry): DexTrackerUndo {
        val db = firestore ?: error("Dex Tracker is not set up")
        val uid = _uid.value ?: error("Not signed in to Dex Tracker")
        val change = DexTrackerDomain.plusOne(entry, System.currentTimeMillis(), UUID.randomUUID().toString())
        val collection = db.document("users/$uid/collections/$collectionId")
        val batch = db.batch()
        batch.update(collection.collection("entries").document(entry.id), change.increments.mapValues { FieldValue.increment(it.value) })
        if (change.gained != 0) {
            batch.set(
                collection.collection("activity").document(change.day),
                mapOf("date" to change.day, "gained" to FieldValue.increment(change.gained.toLong())),
                SetOptions.merge()
            )
        }
        batch.set(collection.collection("events").document(change.event["id"] as String), change.event)
        // Not awaited: offline, the write waits in Firestore's queue and the snapshot shows it at once.
        batch.commit()
        return DexTrackerUndo(collectionId, entry.id, change.gained, change.event["id"] as String, change.day)
    }

    /** Takes a "+1 caught" back, as the web app's Undo does (revertPatch, `undoes`). */
    fun undo(undo: DexTrackerUndo) {
        val db = firestore ?: return
        val uid = _uid.value ?: return
        val collection = db.document("users/$uid/collections/${undo.collectionId}")
        val batch = db.batch()
        batch.update(
            collection.collection("entries").document(undo.entryId),
            mapOf("unassigned" to FieldValue.increment(-1), "revision" to FieldValue.increment(1))
        )
        if (undo.gained != 0) {
            batch.set(
                collection.collection("activity").document(undo.day),
                mapOf("date" to undo.day, "gained" to FieldValue.increment(-undo.gained.toLong())),
                SetOptions.merge()
            )
        }
        batch.delete(collection.collection("events").document(undo.eventId))
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

    private fun toEntry(doc: DocumentSnapshot): DexTrackerEntry? = runCatching {
        @Suppress("UNCHECKED_CAST")
        val owned = (doc.get("owned") as? Map<String, Any?>).orEmpty()
            .mapValues { (it.value as? Number)?.toInt() ?: 0 }
            .filterValues { it > 0 }
        DexTrackerEntry(
            id = doc.id,
            pokemonId = doc.getLong("pokemonId")?.toInt() ?: return null,
            name = doc.getString("name") ?: return null,
            target = doc.getLong("target")?.toInt() ?: 1,
            owned = owned,
            unassigned = doc.getLong("unassigned")?.toInt() ?: 0,
            order = doc.getLong("order")?.toInt() ?: 0
        )
    }.getOrNull()

    private suspend fun <T> await(task: Task<T>): T = withContext(Dispatchers.IO) { Tasks.await(task) }

    companion object {
        private const val APP_NAME = "dextracker"

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
