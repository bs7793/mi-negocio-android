package superapps.minegocio.ui.workspacesession

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import superapps.minegocio.ui.auth.AuthSessionManager
import superapps.minegocio.ui.categoriesscreen.SupabaseProvider
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

@Serializable
private data class ListMyWorkspacesPayload(
    @SerialName("p_selected_workspace_id")
    val selectedWorkspaceId: String? = null,
)

class WorkspaceSessionRepository(
    private val authSessionManager: AuthSessionManager = AuthSessionManager(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun listMyWorkspaces(selectedWorkspaceId: String?): List<WorkspaceSummary> = withContext(Dispatchers.IO) {
        SupabaseProvider.assertConfigured()
        val endpoint = "${SupabaseProvider.restUrl}/rpc/list_my_workspaces"
        val body = json.encodeToString(
            ListMyWorkspacesPayload(selectedWorkspaceId = selectedWorkspaceId),
        )
        val result = post(endpoint, body)
        if (result.code !in 200..299) {
            throw IOException(parseSupabaseError(result.body, "Failed to list workspaces (${result.code})"))
        }
        json.decodeFromString(result.body)
    }

    suspend fun setActiveWorkspace(workspaceId: String): SetActiveWorkspaceResponse = withContext(Dispatchers.IO) {
        if (workspaceId.isBlank()) {
            throw IOException("Selecciona un workspace antes de continuar.")
        }
        SetActiveWorkspaceResponse(
            success = true,
            activeWorkspaceId = workspaceId,
        )
    }

    private suspend fun post(endpoint: String, body: String): HttpResult {
        val token = authSessionManager.getSupabaseAccessToken(forceRefresh = false)
        val first = executePost(endpoint, body, token)
        if (first.code != 401) return first
        val refreshed = authSessionManager.getSupabaseAccessToken(forceRefresh = true)
        return executePost(endpoint, body, refreshed)
    }

    private fun executePost(endpoint: String, body: String, accessToken: String): HttpResult {
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
            connection.outputStream.use { out ->
                out.write(body.toByteArray(StandardCharsets.UTF_8))
            }
            val code = connection.responseCode
            val responseBody = readBody(connection, code in 200..299)
            return HttpResult(code, responseBody)
        } finally {
            connection.disconnect()
        }
    }
}

private data class HttpResult(
    val code: Int,
    val body: String,
)

private fun parseSupabaseError(rawBody: String, fallback: String): String {
    if (rawBody.isBlank()) return fallback
    val message = Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(rawBody)?.groupValues?.getOrNull(1)
    return when (message) {
        "workspace_required" -> "Selecciona un workspace antes de continuar."
        "workspace_forbidden" -> "No tienes permisos en el workspace seleccionado."
        "cross_workspace_reference" -> "El recurso solicitado pertenece a otro workspace."
        else -> message ?: fallback
    }
}

private fun readBody(connection: HttpURLConnection, isSuccess: Boolean): String {
    val stream = if (isSuccess) connection.inputStream else connection.errorStream
    if (stream == null) return ""
    return stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
}
