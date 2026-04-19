package superapps.minegocio.ui.categoriesscreen

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import com.google.firebase.crashlytics.FirebaseCrashlytics
import superapps.minegocio.ui.auth.AuthSessionManager
import superapps.minegocio.ui.workspacesession.WorkspaceSelectionStore
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets

@Serializable
private data class CategoryInsertPayload(
    @SerialName("workspace_id")
    val workspaceId: String,
    val name: String,
    val description: String? = null,
)

@Serializable
private data class CategoryUpdatePayload(
    val name: String,
    val description: String? = null,
)

class CategoriesRepository(
    private val authSessionManager: AuthSessionManager = AuthSessionManager(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var cachedWorkspaceId: String? = null
    private var cachedWorkspaceForUserId: String? = null

    suspend fun fetchCategories(): List<Category> = withContext(Dispatchers.IO) {
        SupabaseProvider.assertConfigured()
        val workspaceId = primaryWorkspaceId()
        val encodedWorkspace = workspaceId.urlEncode()
        val endpoint =
            "${SupabaseProvider.restUrl}/categories" +
                "?select=id,workspace_id,name,description" +
                "&workspace_id=eq.$encodedWorkspace" +
                "&order=created_at.desc"
        get(endpoint)
    }

    suspend fun createCategory(name: String, description: String?): Category = withContext(Dispatchers.IO) {
        SupabaseProvider.assertConfigured()
        val workspaceId = requireSelectedWorkspaceId()
        val payload = CategoryInsertPayload(
            workspaceId = workspaceId,
            name = name.trim(),
            description = description?.trim().takeUnless { it.isNullOrBlank() },
        )
        val endpoint = "${SupabaseProvider.restUrl}/categories?select=id,workspace_id,name,description"
        post(endpoint, payload)
    }

    suspend fun updateCategory(id: Long, name: String, description: String?): Category = withContext(Dispatchers.IO) {
        SupabaseProvider.assertConfigured()
        val workspaceId = requireSelectedWorkspaceId()
        val payload = CategoryUpdatePayload(
            name = name.trim(),
            description = description?.trim().takeUnless { it.isNullOrBlank() },
        )
        val encodedId = id.toString().urlEncode()
        val encodedWorkspace = workspaceId.urlEncode()
        val endpoint =
            "${SupabaseProvider.restUrl}/categories" +
                "?id=eq.$encodedId" +
                "&workspace_id=eq.$encodedWorkspace" +
                "&select=id,workspace_id,name,description"
        patch(endpoint, payload)
    }

    private fun requireSelectedWorkspaceId(): String {
        return WorkspaceSelectionStore.selectedWorkspaceId
            ?.takeIf { it.isNotBlank() }
            ?: throw IOException("Selecciona un workspace antes de continuar.")
    }

    private suspend fun primaryWorkspaceId(): String {
        val uid = authSessionManager.currentUserIdOrNull()
        val selectedWorkspaceId = WorkspaceSelectionStore.selectedWorkspaceId
        if (!selectedWorkspaceId.isNullOrBlank()) {
            cachedWorkspaceId = selectedWorkspaceId
            cachedWorkspaceForUserId = uid
            return selectedWorkspaceId
        }
        if (
            cachedWorkspaceId != null &&
            uid != null &&
            cachedWorkspaceForUserId == uid
        ) {
            return cachedWorkspaceId!!
        }
        val id = fetchPrimaryWorkspaceId()
        cachedWorkspaceId = id
        cachedWorkspaceForUserId = uid
        return id
    }

    private suspend fun fetchPrimaryWorkspaceId(): String {
        return try {
            val endpoint = "${SupabaseProvider.restUrl}/rpc/get_my_primary_workspace_id"
            val token = authSessionManager.getSupabaseAccessToken(forceRefresh = false)
            val first = executePostEmpty(endpoint, token)
            if (first.code != 401) {
                parseWorkspaceRpcResponse(first.code, first.body)
            } else {
                val refreshed = authSessionManager.getSupabaseAccessToken(forceRefresh = true)
                val retry = executePostEmpty(endpoint, refreshed)
                parseWorkspaceRpcResponse(retry.code, retry.body)
            }
        } catch (e: Exception) {
            reportWorkspaceResolutionToCrashlytics(e)
            throw e
        }
    }

    private fun parseWorkspaceRpcResponse(code: Int, body: String): String {
        if (code !in 200..299) {
            throw IOException(parseSupabaseError(body, "Failed to resolve workspace ($code)"))
        }
        val trimmed = body.trim()
        if (trimmed.isEmpty()) {
            throw IOException("Workspace id response was empty")
        }
        val element = try {
            json.parseToJsonElement(trimmed)
        } catch (e: Exception) {
            throw IOException(
                parseSupabaseError(trimmed, "Invalid JSON in workspace response"),
                e,
            )
        }
        return when (element) {
            JsonNull -> throw IOException("Workspace id was null")
            is JsonPrimitive -> {
                if (!element.isString) {
                    throw IOException("Workspace id must be a JSON string, got: ${element.content}")
                }
                val id = element.content
                if (id.isBlank()) {
                    throw IOException("Workspace id was empty")
                }
                id
            }
            else -> throw IOException("Expected a JSON string scalar for workspace id")
        }
    }

    private suspend fun get(
        endpoint: String,
    ): List<Category> {
        val token = authSessionManager.getSupabaseAccessToken(forceRefresh = false)
        val firstAttempt = executeGet(endpoint, token)
        if (firstAttempt.code != 401) {
            return handleGetResponse(firstAttempt.code, firstAttempt.body)
        }
        val refreshedToken = authSessionManager.getSupabaseAccessToken(forceRefresh = true)
        val retry = executeGet(endpoint, refreshedToken)
        return handleGetResponse(retry.code, retry.body)
    }

    private suspend fun post(
        endpoint: String,
        payload: CategoryInsertPayload,
    ): Category {
        val rawPayload = json.encodeToString(payload)
        val token = authSessionManager.getSupabaseAccessToken(forceRefresh = false)
        val firstAttempt = executePost(endpoint, rawPayload, token)
        if (firstAttempt.code != 401) {
            return handleMutationResponse(firstAttempt.code, firstAttempt.body, "Failed to create category")
        }
        val refreshedToken = authSessionManager.getSupabaseAccessToken(forceRefresh = true)
        val retry = executePost(endpoint, rawPayload, refreshedToken)
        return handleMutationResponse(retry.code, retry.body, "Failed to create category")
    }

    private suspend fun patch(
        endpoint: String,
        payload: CategoryUpdatePayload,
    ): Category {
        val rawPayload = json.encodeToString(payload)
        val token = authSessionManager.getSupabaseAccessToken(forceRefresh = false)
        val firstAttempt = executePatch(endpoint, rawPayload, token)
        if (firstAttempt.code != 401) {
            return handleMutationResponse(firstAttempt.code, firstAttempt.body, "Failed to update category")
        }
        val refreshedToken = authSessionManager.getSupabaseAccessToken(forceRefresh = true)
        val retry = executePatch(endpoint, rawPayload, refreshedToken)
        return handleMutationResponse(retry.code, retry.body, "Failed to update category")
    }

    private fun executeGet(
        endpoint: String,
        accessToken: String,
    ): HttpResult {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 15000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("apikey", SupabaseProvider.anonKey)
            setRequestProperty("Authorization", "Bearer $accessToken")
        }
        try {
            val code = connection.responseCode
            val body = readBody(connection, code in 200..299)
            return HttpResult(code = code, body = body)
        } finally {
            connection.disconnect()
        }
    }

    private fun executePost(
        endpoint: String,
        rawPayload: String,
        accessToken: String,
    ): HttpResult {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10000
            readTimeout = 15000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Prefer", "return=representation")
            setRequestProperty("apikey", SupabaseProvider.anonKey)
            setRequestProperty("Authorization", "Bearer $accessToken")
        }

        try {
            connection.outputStream.use { output ->
                output.write(rawPayload.toByteArray(StandardCharsets.UTF_8))
            }
            val code = connection.responseCode
            val body = readBody(connection, code in 200..299)
            return HttpResult(code = code, body = body)
        } finally {
            connection.disconnect()
        }
    }

    private fun executePatch(
        endpoint: String,
        rawPayload: String,
        accessToken: String,
    ): HttpResult {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "PATCH"
            connectTimeout = 10000
            readTimeout = 15000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Prefer", "return=representation")
            setRequestProperty("apikey", SupabaseProvider.anonKey)
            setRequestProperty("Authorization", "Bearer $accessToken")
        }

        try {
            connection.outputStream.use { output ->
                output.write(rawPayload.toByteArray(StandardCharsets.UTF_8))
            }
            val code = connection.responseCode
            val body = readBody(connection, code in 200..299)
            return HttpResult(code = code, body = body)
        } finally {
            connection.disconnect()
        }
    }

    private fun executePostEmpty(
        endpoint: String,
        accessToken: String,
    ): HttpResult = executePost(endpoint, "{}", accessToken)

    private fun handleGetResponse(code: Int, body: String): List<Category> {
        if (code !in 200..299) {
            throw IOException(parseSupabaseError(body, "Failed to fetch categories ($code)"))
        }
        return json.decodeFromString(body)
    }

    private fun handleMutationResponse(code: Int, body: String, failureLabel: String): Category {
        if (code !in 200..299) {
            throw IOException(parseSupabaseError(body, "$failureLabel ($code)"))
        }
        val inserted = json.decodeFromString<List<Category>>(body)
        return inserted.firstOrNull()
            ?: throw IOException("Category operation returned an empty response")
    }
}

private data class HttpResult(
    val code: Int,
    val body: String,
)

private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.toString())

private fun readBody(
    connection: HttpURLConnection,
    isSuccess: Boolean,
): String {
    val stream = if (isSuccess) connection.inputStream else connection.errorStream
    if (stream == null) return ""
    return stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
}

private fun parseSupabaseError(rawBody: String, fallback: String): String {
    if (rawBody.isBlank()) return fallback
    val message = Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(rawBody)?.groupValues?.getOrNull(1)
    return when (message) {
        "workspace_required" -> "Selecciona un workspace antes de continuar."
        "workspace_forbidden" -> "No tienes permisos en el workspace seleccionado."
        "cross_workspace_reference" -> "Los datos seleccionados pertenecen a otro workspace."
        else -> message ?: fallback
    }
}

private fun reportWorkspaceResolutionToCrashlytics(throwable: Throwable) {
    try {
        FirebaseCrashlytics.getInstance().apply {
            log("workspace_rpc get_my_primary_workspace_id failed")
            recordException(throwable)
        }
    } catch (_: Exception) {
        // Avoid masking the original error if Crashlytics is unavailable.
    }
}
