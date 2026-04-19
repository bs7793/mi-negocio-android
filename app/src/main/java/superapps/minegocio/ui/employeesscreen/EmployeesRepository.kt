package superapps.minegocio.ui.employeesscreen

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
private data class InviteEmployeePayload(
    val email: String,
    val role: String,
)

@Serializable
private data class UpdateEmployeePayload(
    @SerialName("target_user_id")
    val targetUserId: String,
    val role: String,
    val status: String,
)

class EmployeesRepository(
    private val authSessionManager: AuthSessionManager = AuthSessionManager(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchEmployees(): List<Employee> = withContext(Dispatchers.IO) {
        SupabaseProvider.assertConfigured()
        postRpcList(
            rpcName = "list_workspace_members",
            body = "{}",
        )
    }

    suspend fun inviteEmployee(
        email: String,
        role: String,
    ): EmployeeMutationResult = withContext(Dispatchers.IO) {
        SupabaseProvider.assertConfigured()
        val payload = InviteEmployeePayload(email = email.trim(), role = role.trim())
        postRpcMutation(
            rpcName = "invite_workspace_member",
            body = json.encodeToString(payload),
            fallbackLabel = "Failed to invite employee",
        )
    }

    suspend fun updateEmployee(
        targetUserId: String,
        role: String,
        status: String,
    ): EmployeeMutationResult = withContext(Dispatchers.IO) {
        SupabaseProvider.assertConfigured()
        val payload = UpdateEmployeePayload(
            targetUserId = targetUserId,
            role = role.trim(),
            status = status.trim(),
        )
        postRpcMutation(
            rpcName = "update_workspace_member_role_status",
            body = json.encodeToString(payload),
            fallbackLabel = "Failed to update employee",
        )
    }

    private suspend fun postRpcList(
        rpcName: String,
        body: String,
    ): List<Employee> {
        val endpoint = "${SupabaseProvider.restUrl}/rpc/$rpcName"
        val token = authSessionManager.getSupabaseAccessToken(forceRefresh = false)
        val first = executePost(endpoint, body, token)
        if (first.code != 401) {
            return handleListResponse(first.code, first.body, rpcName)
        }
        val refreshed = authSessionManager.getSupabaseAccessToken(forceRefresh = true)
        val retry = executePost(endpoint, body, refreshed)
        return handleListResponse(retry.code, retry.body, rpcName)
    }

    private suspend fun postRpcMutation(
        rpcName: String,
        body: String,
        fallbackLabel: String,
    ): EmployeeMutationResult {
        val endpoint = "${SupabaseProvider.restUrl}/rpc/$rpcName"
        val token = authSessionManager.getSupabaseAccessToken(forceRefresh = false)
        val first = executePost(endpoint, body, token)
        if (first.code != 401) {
            return handleMutationResponse(first.code, first.body, fallbackLabel)
        }
        val refreshed = authSessionManager.getSupabaseAccessToken(forceRefresh = true)
        val retry = executePost(endpoint, body, refreshed)
        return handleMutationResponse(retry.code, retry.body, fallbackLabel)
    }

    private fun handleListResponse(code: Int, rawBody: String, rpcName: String): List<Employee> {
        if (code !in 200..299) {
            throw IOException(parseSupabaseError(rawBody, "Failed RPC $rpcName ($code)"))
        }
        return json.decodeFromString(rawBody)
    }

    private fun handleMutationResponse(
        code: Int,
        rawBody: String,
        fallbackLabel: String,
    ): EmployeeMutationResult {
        if (code !in 200..299) {
            throw IOException(parseSupabaseError(rawBody, "$fallbackLabel ($code)"))
        }
        return json.decodeFromString(rawBody)
    }

    private fun executePost(endpoint: String, rawPayload: String, accessToken: String): HttpResult {
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
                output.write(rawPayload.toByteArray(StandardCharsets.UTF_8))
            }
            val code = connection.responseCode
            val body = readBody(connection, code in 200..299)
            return HttpResult(code = code, body = body)
        } finally {
            connection.disconnect()
        }
    }
}

private data class HttpResult(
    val code: Int,
    val body: String,
)

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
