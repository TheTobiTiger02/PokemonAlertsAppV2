package com.example.pokemonalertsv2.account

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** The signed-in app account, as the UI shows it. */
data class Account(val uid: String, val email: String?, val displayName: String?)

/** Sign-in failed for a reason the user can act on. */
class AccountSignInException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The app account: Google sign-in through Credential Manager, then Firebase Auth, which keeps
 * the session and refreshes the ID token the backend checks. The account is optional: the app
 * works signed out, and signing in turns on device sync ([AccountSync]).
 */
object AccountRepository {
    private const val TAG = "AccountRepository"
    private const val TOKEN_TIMEOUT_SECONDS = 15L

    private val _account = MutableStateFlow<Account?>(null)
    val account: StateFlow<Account?> = _account.asStateFlow()

    @Volatile
    private var listening = false

    /** Firebase Auth, or null when this build has no Firebase configuration. */
    private fun auth(): FirebaseAuth? = runCatching { FirebaseAuth.getInstance() }.getOrNull()

    /** Starts following the Firebase session; called once from the Application. */
    fun init() {
        if (listening) return
        val auth = auth() ?: return
        listening = true
        _account.value = auth.currentUser?.toAccount()
        auth.addAuthStateListener { _account.value = it.currentUser?.toAccount() }
    }

    private fun FirebaseUser.toAccount() = Account(uid, email, displayName)

    /**
     * The Google OAuth web client id the google-services plugin generates from
     * google-services.json. Read by name: a configuration without an OAuth client has none,
     * and the build must still compile.
     */
    @SuppressLint("DiscouragedApi")
    private fun webClientId(context: Context): String? {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (id == 0) null else context.getString(id).takeIf { it.isNotBlank() }
    }

    /** Whether this build can sign in at all. */
    fun isAvailable(context: Context): Boolean = webClientId(context) != null && auth() != null

    /**
     * Shows Google's account picker and signs in to Firebase. [activityContext] must be an
     * Activity. Returns null when the user closed the picker.
     */
    // Lint does not see the companion constants below as handling GoogleIdTokenCredential; they do.
    @SuppressLint("CredentialManagerSignInWithGoogle")
    suspend fun signIn(activityContext: Context): Account? {
        val auth = auth() ?: throw AccountSignInException("Firebase is not configured")
        val clientId = webClientId(activityContext) ?: throw AccountSignInException("Google sign-in is not configured")
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
            .build()
        val credential = try {
            CredentialManager.create(activityContext).getCredential(activityContext, request).credential
        } catch (cancelled: GetCredentialCancellationException) {
            return null
        } catch (none: NoCredentialException) {
            throw AccountSignInException("No Google account on this device", none)
        }
        val googleTypes = setOf(
            GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL,
            GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL
        )
        if (credential !is CustomCredential || credential.type !in googleTypes) {
            throw AccountSignInException("Unexpected credential type")
        }
        val google = GoogleIdTokenCredential.createFrom(credential.data)
        val result = await(auth.signInWithCredential(GoogleAuthProvider.getCredential(google.idToken, null)))
        val user = result.user ?: throw AccountSignInException("Sign-in returned no user")
        return user.toAccount().also { _account.value = it }
    }

    fun isSignedIn(): Boolean = auth()?.currentUser != null

    /** The current ID token, refreshed when it is about to run out; null when signed out. */
    suspend fun idToken(): String? = withContext(Dispatchers.IO) { idTokenBlocking() }

    /** For OkHttp's interceptor thread. Never on the main thread. */
    fun idTokenBlocking(): String? {
        val user = auth()?.currentUser ?: return null
        return runCatching { Tasks.await(user.getIdToken(false), TOKEN_TIMEOUT_SECONDS, TimeUnit.SECONDS).token }
            .onFailure { Log.w(TAG, "Could not get an ID token", it) }
            .getOrNull()
    }

    /** Signs out of Firebase and forgets the Google account choice, so the picker shows next time. */
    suspend fun signOut(context: Context) {
        auth()?.signOut()
        _account.value = null
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
            .onFailure { Log.w(TAG, "Could not clear the credential state", it) }
    }

    private suspend fun <T> await(task: Task<T>): T = withContext(Dispatchers.IO) { Tasks.await(task) }
}
