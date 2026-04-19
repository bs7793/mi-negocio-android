package superapps.minegocio.ui.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import superapps.minegocio.ui.categoriesscreen.SupabaseProvider

class AuthSessionManager(
    private val supabase: SupabaseClient = SupabaseProvider.client,
) {
    private val sessionMutex = Mutex()

    fun observeAuthState(): Flow<SupabaseAuthUser?> = flow {
        emit(currentUserOrNull())
        supabase.auth.sessionStatus.collect {
            emit(currentUserOrNull())
        }
    }

    suspend fun ensureAnonymousSession() {
        sessionMutex.withLock {
            if (supabase.auth.currentSessionOrNull() == null) {
                supabase.auth.signInAnonymously(data = buildJsonObject { })
            }
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
        sessionMutex.withLock {
            supabase.auth.signOut()
            if (supabase.auth.currentSessionOrNull() == null) {
                supabase.auth.signInAnonymously(data = buildJsonObject { })
            }
        }
    }

    suspend fun acceptInviteCode(inviteCode: String) {
        require(inviteCode.isNotBlank()) { "Invite code is required" }
        val endpoint = "${SupabaseProvider.restUrl}/rpc/accept_workspace_invite"
        val payload = """{"invite_token":"${inviteCode.trim()}"}"""
        val token = getSupabaseAccessToken(forceRefresh = false)
        val first = executePost(endpoint, payload, token)
        val result = if (first.code == 401) {
            val refreshed = getSupabaseAccessToken(forceRefresh = true)
            executePost(endpoint, payload, refreshed)
        } else {
            first
        }
        if (result.code !in 200..299) {
            throw IOException(parseSupabaseError(result.body, "Failed to accept invite code (${result.code})"))
        }
    }

    suspend fun getSupabaseAccessToken(forceRefresh: Boolean = false): String {
        return getSupabaseAccessTokenOrNull(forceRefresh = forceRefresh)
            ?: throw IllegalStateException("No active Supabase session")
    }

    suspend fun getSupabaseAccessTokenOrNull(forceRefresh: Boolean = false): String? {
        repeat(3) { attempt ->
            val token = sessionMutex.withLock {
                var session = supabase.auth.currentSessionOrNull()
                if (session != null && forceRefresh && attempt == 0) {
                    runCatching { supabase.auth.refreshCurrentSession() }
                    session = supabase.auth.currentSessionOrNull()
                }
                session?.accessToken
            }
            if (!token.isNullOrBlank()) return token

            if (attempt < 2) {
                delay((attempt + 1) * 120L)
            }
        }
        return null
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

private data class HttpResult(
    val code: Int,
    val body: String,
)

private fun executePost(endpoint: String, payload: String, accessToken: String): HttpResult {
    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 10000
        readTimeout = 15000
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        setRequestProperty("Accept", "application/json")
        setRequestProperty("apikey", SupabaseProvider.anonKey)
        setRequestProperty("Authorization", "Bearer $accessToken")
    }
    try {
        connection.outputStream.use { output ->
            output.write(payload.toByteArray(StandardCharsets.UTF_8))
        }
        val code = connection.responseCode
        val body = readBody(connection, code in 200..299)
        return HttpResult(code, body)
    } finally {
        connection.disconnect()
    }
}

private fun readBody(connection: HttpURLConnection, isSuccess: Boolean): String {
    val stream = if (isSuccess) connection.inputStream else connection.errorStream
    if (stream == null) return ""
    return stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
}

private fun parseSupabaseError(rawBody: String, fallback: String): String {
    if (rawBody.isBlank()) return fallback
    val message = Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(rawBody)?.groupValues?.getOrNull(1)
    return message ?: fallback
}

private fun UserInfo.isAnonymousSupabaseUser(): Boolean {
    if (isAnonymous == true) {
        return true
    }
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
