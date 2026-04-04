package superapps.minegocio.ui.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AuthSessionManager(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
) {
    fun observeAuthState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser)
        }
        firebaseAuth.addAuthStateListener(listener)
        trySend(firebaseAuth.currentUser)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    suspend fun ensureAnonymousSession() {
        if (firebaseAuth.currentUser == null) {
            firebaseAuth.signInAnonymously().awaitResult()
        }
    }

    suspend fun signInWithGoogleIdToken(idToken: String) {
        require(idToken.isNotBlank()) { "Google ID token is required" }

        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val currentUser = firebaseAuth.currentUser

        if (currentUser?.isAnonymous == true) {
            try {
                currentUser.linkWithCredential(credential).awaitResult()
                return
            } catch (_: FirebaseAuthUserCollisionException) {
                // The credential already belongs to an existing account.
                // Sign into that account instead of failing the flow.
            }
        }

        firebaseAuth.signInWithCredential(credential).awaitResult()
    }

    suspend fun signOutAndRecreateAnonymous() {
        firebaseAuth.signOut()
        ensureAnonymousSession()
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result ->
        if (continuation.isActive) {
            continuation.resume(result)
        }
    }
    addOnFailureListener { error ->
        if (continuation.isActive) {
            continuation.resumeWithException(error)
        }
    }
}
