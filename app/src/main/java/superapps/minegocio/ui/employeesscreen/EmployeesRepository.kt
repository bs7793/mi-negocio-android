package superapps.minegocio.ui.employeesscreen

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import superapps.minegocio.ui.auth.AuthSessionManager
import superapps.minegocio.ui.categoriesscreen.SupabaseProvider
import superapps.minegocio.ui.workspacesession.WorkspaceSelectionStore
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

@Serializable
private data class UpdateEmployeePayload(
    @SerialName("target_user_id")
    val targetUserId: String,
    val role: String,
    val status: String,
    @SerialName("p_workspace_id")
    val workspaceId: String? = null,
)

@Serializable
private data class CreateInviteCodePayload(
    @SerialName("p_role")
    val role: String,
    @SerialName("p_workspace_id")
    val workspaceId: String? = null,
)

@Serializable
private data class RevokeInviteCodePayload(
    @SerialName("p_invite_code")
    val inviteCode: String,
    @SerialName("p_workspace_id")
    val workspaceId: String? = null,
)

@Serializable
private data class ListInviteCodesPayload(
    @SerialName("p_workspace_id")
    val workspaceId: String? = null,
)

@Serializable
private data class ListMembersPayload(
    @SerialName("p_workspace_id")
    val workspaceId: String? = null,
)

class EmployeesRepository(
    private val authSessionManager: AuthSessionManager = AuthSessionManager(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchEmployees(): List<Employee> = withContext(Dispatchers.IO) {
        SupabaseProvider.assertConfigured()
        val workspaceId = requireSelectedWorkspaceId()
        val body = json.encodeToString(
            ListMembersPayload(workspaceId = workspaceId),
        )
        postRpcList(
            rpcName = "list_workspace_members",
            body = body,
        )
    }

    suspend fun createInviteCode(role: String): InviteCodeResult = withContext(Dispatchers.IO) {
        SupabaseProvider.assertConfigured()
        val workspaceId = requireSelectedWorkspaceId()
        val payload = CreateInviteCodePayload(
            role = role.trim(),
            workspaceId = workspaceId,
        )
        postInviteCodeMutation(
            rpcName = "create_workspace_invite_code",
            body = json.encodeToString(payload),
            fallbackLabel = "Failed to create invite code",
        )
    }

    suspend fun listInviteCodes(): List<WorkspaceInviteCode> = withContext(Dispatchers.IO) {
        SupabaseProvider.assertConfigured()
        val workspaceId = requireSelectedWorkspaceId()
        val endpoint = "${SupabaseProvider.restUrl}/rpc/list_workspace_invite_codes"
        val body = json.encodeToString(
            ListInviteCodesPayload(workspaceId = workspaceId),
        )
        val first = postRaw(endpoint = endpoint, body = body)
        val result = if (first.code == 401) postRaw(endpoint = endpoint, body = body, forceRefresh = true) else first
        if (result.code !in 200..299) {
            throw IOException(parseSupabaseError(result.body, "Failed to list invite codes (${result.code})"))
        }
        json.decodeFromString(result.body)
    }

    suspend fun revokeInviteCode(inviteCode: String): EmployeeMutationResult = withContext(Dispatchers.IO) {
        SupabaseProvider.assertConfigured()
        val workspaceId = requireSelectedWorkspaceId()
        val payload = RevokeInviteCodePayload(
            inviteCode = inviteCode.trim().uppercase(),
            workspaceId = workspaceId,
        )
        postEmployeeMutation(
            rpcName = "revoke_workspace_invite_code",
            body = json.encodeToString(payload),
            fallbackLabel = "Failed to revoke invite code",
        )
    }

    suspend fun acceptInviteCode(code: String): EmployeeMutationResult = withContext(Dispatchers.IO) {
        SupabaseProvider.assertConfigured()
        val endpoint = "${SupabaseProvider.restUrl}/rpc/accept_workspace_invite"
        val payload = mapOf("invite_token" to code.trim())
        val first = postRaw(endpoint = endpoint, body = json.encodeToString(payload))
        val result = if (first.code == 401) {
            postRaw(endpoint = endpoint, body = json.encodeToString(payload), forceRefresh = true)
        } else {
            first
        }
        if (result.code !in 200..299) {
            throw IOException(parseSupabaseError(result.body, "Failed to accept invite code (${result.code})"))
        }
        json.decodeFromString(result.body)
    }

    suspend fun updateEmployee(
        targetUserId: String,
        role: String,
        status: String,
    ): EmployeeMutationResult = withContext(Dispatchers.IO) {
        SupabaseProvider.assertConfigured()
        val workspaceId = requireSelectedWorkspaceId()
        val payload = UpdateEmployeePayload(
            targetUserId = targetUserId,
            role = role.trim(),
            status = status.trim(),
            workspaceId = workspaceId,
        )
        postEmployeeMutation(
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

    private suspend fun postEmployeeMutation(
        rpcName: String,
        body: String,
        fallbackLabel: String,
    ): EmployeeMutationResult {
        val endpoint = "${SupabaseProvider.restUrl}/rpc/$rpcName"
        val first = postRaw(endpoint, body)
        if (first.code != 401) {
            return handleEmployeeMutationResponse(first.code, first.body, fallbackLabel)
        }
        val retry = postRaw(endpoint, body, forceRefresh = true)
        return handleEmployeeMutationResponse(retry.code, retry.body, fallbackLabel)
    }

    private fun handleListResponse(code: Int, rawBody: String, rpcName: String): List<Employee> {
        if (code !in 200..299) {
            throw IOException(parseSupabaseError(rawBody, "Failed RPC $rpcName ($code)"))
        }
        return json.decodeFromString(rawBody)
    }

    private suspend fun postInviteCodeMutation(
        rpcName: String,
        body: String,
        fallbackLabel: String,
    ): InviteCodeResult {
        val endpoint = "${SupabaseProvider.restUrl}/rpc/$rpcName"
        val first = postRaw(endpoint, body)
        if (first.code != 401) {
            return handleInviteCodeMutationResponse(first.code, first.body, fallbackLabel)
        }
        val retry = postRaw(endpoint, body, forceRefresh = true)
        return handleInviteCodeMutationResponse(retry.code, retry.body, fallbackLabel)
    }

    private fun handleEmployeeMutationResponse(
        code: Int,
        rawBody: String,
        fallbackLabel: String,
    ): EmployeeMutationResult {
        if (code !in 200..299) {
            throw IOException(parseSupabaseError(rawBody, "$fallbackLabel ($code)"))
        }
        return json.decodeFromString(rawBody)
    }

    private fun handleInviteCodeMutationResponse(
        code: Int,
        rawBody: String,
        fallbackLabel: String,
    ): InviteCodeResult {
        if (code !in 200..299) {
            throw IOException(parseSupabaseError(rawBody, "$fallbackLabel ($code)"))
        }
        return json.decodeFromString(rawBody)
    }

    private fun requireSelectedWorkspaceId(): String {
        return WorkspaceSelectionStore.selectedWorkspaceId
            ?.takeIf { it.isNotBlank() }
            ?: throw IOException("Selecciona un workspace antes de continuar.")
    }

    private suspend fun postRaw(
        endpoint: String,
        body: String,
        forceRefresh: Boolean = false,
    ): HttpResult {
        val token = authSessionManager.getSupabaseAccessToken(forceRefresh = forceRefresh)
        return executePost(endpoint, body, token)
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
    return when (message) {
        "workspace_required" -> "Selecciona un workspace antes de continuar."
        "workspace_forbidden" -> "No tienes permisos en el workspace seleccionado."
        "cross_workspace_reference" -> "El recurso no pertenece al workspace activo."
        else -> message ?: fallback
    }
}
