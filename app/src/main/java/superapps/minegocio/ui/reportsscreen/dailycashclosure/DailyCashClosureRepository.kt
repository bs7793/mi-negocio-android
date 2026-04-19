package superapps.minegocio.ui.reportsscreen.dailycashclosure

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
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class DailyCashClosureRepository(
    private val authSessionManager: AuthSessionManager = AuthSessionManager(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchWarehouses(): List<DailyCashClosureWarehouse> = withContext(Dispatchers.IO) {
        SupabaseProvider.assertConfigured()
        val workspaceId = requireSelectedWorkspaceId()
        val endpoint =
            "${SupabaseProvider.restUrl}/warehouses" +
                "?select=id,workspace_id,name,location,aisle,shelf,level,position" +
                "&workspace_id=eq.${workspaceId.urlEncode()}" +
                "&order=name.asc"
        val result = get(endpoint)
        if (result.code !in 200..299) {
            throw IOException(parseSupabaseError(result.body, "Failed to fetch warehouses (${result.code})"))
        }
        return@withContext json.decodeFromString(result.body)
    }

    suspend fun fetchDailySummary(
        warehouseId: Long?,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): DailyCashClosureSummary = withContext(Dispatchers.IO) {
        SupabaseProvider.assertConfigured()
        val workspaceId = requireSelectedWorkspaceId()
        val endpoint = "${SupabaseProvider.restUrl}/rpc/get_sales_daily_summary"
        val dailyRange = calculateLocalDayRange(zoneId)
        val body = json.encodeToString(
            GetSalesDailySummaryPayload(
                warehouseId = warehouseId,
                startAt = dailyRange.startAt,
                endAt = dailyRange.endAt,
                workspaceId = workspaceId,
            ),
        )
        val result = post(endpoint, body)
        if (result.code !in 200..299) {
            throw IOException(parseSupabaseError(result.body, "Failed to fetch sales summary (${result.code})"))
        }
        return@withContext json.decodeFromString(result.body)
    }

    private suspend fun get(endpoint: String): HttpResult {
        val token = authSessionManager.getSupabaseAccessToken(forceRefresh = false)
        val first = execute(endpoint, "GET", token, null)
        if (first.code != 401) return first
        val refreshed = authSessionManager.getSupabaseAccessToken(forceRefresh = true)
        return execute(endpoint, "GET", refreshed, null)
    }

    private suspend fun post(endpoint: String, body: String): HttpResult {
        val token = authSessionManager.getSupabaseAccessToken(forceRefresh = false)
        val first = execute(endpoint, "POST", token, body)
        if (first.code != 401) return first
        val refreshed = authSessionManager.getSupabaseAccessToken(forceRefresh = true)
        return execute(endpoint, "POST", refreshed, body)
    }

    private fun requireSelectedWorkspaceId(): String {
        return WorkspaceSelectionStore.selectedWorkspaceId
            ?.takeIf { it.isNotBlank() }
            ?: throw IOException("Selecciona un workspace antes de continuar.")
    }

    private fun execute(
        endpoint: String,
        method: String,
        accessToken: String,
        body: String?,
    ): HttpResult {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10000
            readTimeout = 15000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("apikey", SupabaseProvider.anonKey)
            setRequestProperty("Authorization", "Bearer $accessToken")
            if (method == "POST") {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Prefer", "return=representation")
            }
        }
        try {
            if (!body.isNullOrEmpty()) {
                connection.outputStream.use { out ->
                    out.write(body.toByteArray(StandardCharsets.UTF_8))
                }
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

private data class DailyRange(
    val startAt: String,
    val endAt: String,
)

private fun calculateLocalDayRange(zoneId: ZoneId): DailyRange {
    val today = LocalDate.now(zoneId)
    val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    val start = today.atStartOfDay(zoneId).toOffsetDateTime().format(formatter)
    val end = today.plusDays(1).atStartOfDay(zoneId).toOffsetDateTime().format(formatter)
    return DailyRange(startAt = start, endAt = end)
}

private fun parseSupabaseError(rawBody: String, fallback: String): String {
    if (rawBody.isBlank()) return fallback
    val message = Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(rawBody)?.groupValues?.getOrNull(1)
    return message ?: fallback
}

private fun readBody(connection: HttpURLConnection, isSuccess: Boolean): String {
    val stream = if (isSuccess) connection.inputStream else connection.errorStream
    if (stream == null) return ""
    return stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
}

@Serializable
private data class GetSalesDailySummaryPayload(
    @SerialName("p_warehouse_id")
    val warehouseId: Long? = null,
    @SerialName("p_start_at")
    val startAt: String? = null,
    @SerialName("p_end_at")
    val endAt: String? = null,
    @SerialName("p_workspace_id")
    val workspaceId: String,
)

private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.toString())
