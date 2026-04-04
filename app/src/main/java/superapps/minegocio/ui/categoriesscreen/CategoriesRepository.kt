package superapps.minegocio.ui.categoriesscreen

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import superapps.minegocio.ui.auth.AuthSessionManager
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
        val workspaceId = primaryWorkspaceId()
        val payload = CategoryInsertPayload(
            workspaceId = workspaceId,
            name = name.trim(),
            description = description?.trim().takeUnless { it.isNullOrBlank() },
        )
        val endpoint = "${SupabaseProvider.restUrl}/categories?select=id,workspace_id,name,description"
        post(endpoint, payload)
    }

    private suspend fun primaryWorkspaceId(): String {
        val uid = authSessionManager.currentUserIdOrNull()
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
        val endpoint = "${SupabaseProvider.restUrl}/rpc/get_my_primary_workspace_id"
        val token = authSessionManager.getSupabaseAccessToken(forceRefresh = false)
        val first = executePostEmpty(endpoint, token)
        if (first.code != 401) {
            return parseWorkspaceRpcResponse(first.code, first.body)
        }
        val refreshed = authSessionManager.getSupabaseAccessToken(forceRefresh = true)
        val retry = executePostEmpty(endpoint, refreshed)
        return parseWorkspaceRpcResponse(retry.code, retry.body)
    }

    private fun parseWorkspaceRpcResponse(code: Int, body: String): String {
        if (code !in 200..299) {
            throw IOException(parseSupabaseError(body, "Failed to resolve workspace ($code)"))
        }
        val trimmed = body.trim().trim('"')
        if (trimmed.isBlank()) {
            throw IOException("Workspace id was empty")
        }
        return trimmed
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
            return handlePostResponse(firstAttempt.code, firstAttempt.body)
        }
        val refreshedToken = authSessionManager.getSupabaseAccessToken(forceRefresh = true)
        val retry = executePost(endpoint, rawPayload, refreshedToken)
        return handlePostResponse(retry.code, retry.body)
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

    private fun handlePostResponse(code: Int, body: String): Category {
        if (code !in 200..299) {
            throw IOException(parseSupabaseError(body, "Failed to create category ($code)"))
        }
        val inserted = json.decodeFromString<List<Category>>(body)
        return inserted.firstOrNull()
            ?: throw IOException("Category create returned an empty response")
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
    return message ?: fallback
}
