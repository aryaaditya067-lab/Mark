package com.example.mark.repository

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

/**
 * Signs the user in with Google so that every device — phone, watch, laptop —
 * shares one Firebase uid, and therefore one Firestore document tree.
 *
 * On the phone this runs an interactive sign-in once.
 * On the watch the Google account is already present, so sign-in is silent.
 */
class AuthRepository {

    private val auth = FirebaseAuth.getInstance()

    val currentUid: String?
        get() = auth.currentUser?.uid

    val isSignedIn: Boolean
        get() = auth.currentUser != null && !auth.currentUser!!.isAnonymous

    /**
     * @param webClientId from google-services.json, field client_type == 3
     */
    fun signInClient(context: Context, webClientId: String): GoogleSignInClient {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(context, options)
    }

    /**
     * Exchanges a Google account for a Firebase session.
     *
     * If the current user is anonymous, the accounts are linked so the existing
     * uid — and all data under it — is preserved. If that link fails because the
     * Google account is already attached to another Firebase user, we sign in to
     * that user instead and the anonymous one is abandoned.
     */
    suspend fun signInWithGoogle(account: GoogleSignInAccount): String {
        val idToken = account.idToken ?: error("Google account returned no ID token.")
        val credential = GoogleAuthProvider.getCredential(idToken, null)

        val existing = auth.currentUser
        if (existing != null && existing.isAnonymous) {
            val linked = runCatching { existing.linkWithCredential(credential).await() }
            if (linked.isSuccess) {
                return linked.getOrThrow().user?.uid ?: error("Link returned no user.")
            }
            // Already linked elsewhere — fall through to a plain sign-in.
        }

        val result = auth.signInWithCredential(credential).await()
        return result.user?.uid ?: error("Sign-in returned no user.")
    }

    /** Silent sign-in. Used on the watch, and on the phone at startup. */
    suspend fun trySilentSignIn(context: Context, webClientId: String): String? {
        auth.currentUser?.let { if (!it.isAnonymous) return it.uid }

        val client = signInClient(context, webClientId)
        val account = runCatching { client.silentSignIn().await() }.getOrNull() ?: return null
        return runCatching { signInWithGoogle(account) }.getOrNull()
    }

    /** Suspends until a uid is available. Throws if the user is not signed in. */
    suspend fun ensureSignedIn(): String =
        auth.currentUser?.uid ?: error("Not signed in. Sign in with Google first.")

    suspend fun signOut(context: Context, webClientId: String) {
        signInClient(context, webClientId).signOut().await()
        auth.signOut()
    }

    companion object {
        val instance: AuthRepository by lazy { AuthRepository() }
    }
}