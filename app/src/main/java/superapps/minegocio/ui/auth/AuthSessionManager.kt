package superapps.minegocio.ui.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.buildJsonObject
import superapps.minegocio.ui.categoriesscreen.SupabaseProvider

class AuthSessionManager(
    private val supabase: SupabaseClient = SupabaseProvider.client,
) {
    fun observeAuthState(): Flow<SupabaseAuthUser?> = flow {
        emit(currentUserOrNull())
        supabase.auth.sessionStatus.collect {
            emit(currentUserOrNull())
        }
    }

    suspend fun ensureAnonymousSession() {
        if (supabase.auth.currentSessionOrNull() == null) {
            supabase.auth.signInAnonymously(data = buildJsonObject { })
        }
    }

    suspend fun signInWithGoogleIdToken(idToken: String) {
        require(idToken.isNotBlank()) { "Google ID token is required" }
        val user = supabase.auth.currentUserOrNull()
        if (user != null && user.isAnonymousSupabaseUser()) {
            supabase.auth.linkIdentityWithIdToken(Google, idToken) { }
        } else {
            supabase.auth.signInWith(IDToken) {
                provider = Google
                this.idToken = idToken
            }
        }
    }

    suspend fun signOutAndRecreateAnonymous() {
        supabase.auth.signOut()
        ensureAnonymousSession()
    }

    suspend fun getSupabaseAccessToken(forceRefresh: Boolean = false): String {
        if (forceRefresh) {
            supabase.auth.refreshCurrentSession()
        }
        val session = supabase.auth.currentSessionOrNull()
            ?: throw IllegalStateException("No active Supabase session")
        return session.accessToken
    }

    fun currentUserIdOrNull(): String? = supabase.auth.currentUserOrNull()?.id

    private suspend fun currentUserOrNull(): SupabaseAuthUser? {
        val user = supabase.auth.currentUserOrNull() ?: return null
        return SupabaseAuthUser(
            id = user.id,
            email = user.email,
            displayName = user.userMetadata?.get("full_name")?.toString(),
            isAnonymous = user.isAnonymousSupabaseUser(),
        )
    }
}

private fun UserInfo.isAnonymousSupabaseUser(): Boolean {
    if (appMetadata?.get("is_anonymous")?.toString()?.toBoolean() == true) {
        return true
    }
    return identities?.any { identity -> identity.provider == "anonymous" } == true
}

data class SupabaseAuthUser(
    val id: String,
    val email: String?,
    val displayName: String?,
    val isAnonymous: Boolean,
)
