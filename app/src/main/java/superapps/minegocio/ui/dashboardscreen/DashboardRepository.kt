package superapps.minegocio.ui.dashboardscreen

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
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class DashboardRepository(
    private val authSessionManager: AuthSessionManager = AuthSessionManager(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchWarehouses(): List<DashboardWarehouse> = withContext(Dispatchers.IO) {
        SupabaseProvider.assertConfigured()
        val endpoint = "${SupabaseProvider.restUrl}/warehouses?select=id,workspace_id,name&order=name.asc"
        val result = get(endpoint)
        if (result.code !in 200..299) {
            throw IOException(parseSupabaseError(result.body, "Failed to fetch warehouses (${result.code})"))
        }
        return@withContext json.decodeFromString(result.body)
    }

    suspend fun fetchSalesFeed(
        warehouseId: Long?,
        zoneId: ZoneId = ZoneId.systemDefault(),
        limit: Int = 50,
    ): List<DashboardSalesFeedItem> = withContext(Dispatchers.IO) {
        SupabaseProvider.assertConfigured()
        val endpoint = "${SupabaseProvider.restUrl}/rpc/get_dashboard_sales_feed"
        val monthlyRange = calculateLocalMonthRange(zoneId)
        val body = json.encodeToString(
            GetDashboardSalesFeedPayload(
                warehouseId = warehouseId,
                startAt = monthlyRange.startAt,
                endAt = monthlyRange.endAt,
                limit = limit,
            ),
        )
        val result = post(endpoint, body)
        if (result.code !in 200..299) {
            throw IOException(parseSupabaseError(result.body, "Failed to fetch sales feed (${result.code})"))
        }
        return@withContext json.decodeFromString(result.body)
    }

    suspend fun fetchMonthlySummary(
        warehouseId: Long?,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): DashboardIncomeStatementSummary = withContext(Dispatchers.IO) {
        SupabaseProvider.assertConfigured()
        val endpoint = "${SupabaseProvider.restUrl}/rpc/get_income_statement_monthly_summary"
        val monthlyRange = calculateLocalMonthRange(zoneId)
        val body = json.encodeToString(
            GetIncomeStatementMonthlySummaryPayload(
                warehouseId = warehouseId,
                startAt = monthlyRange.startAt,
                endAt = monthlyRange.endAt,
            ),
        )
        val result = post(endpoint, body)
        if (result.code !in 200..299) {
            throw IOException(parseSupabaseError(result.body, "Failed to fetch monthly summary (${result.code})"))
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

private data class MonthlyRange(
    val startAt: String,
    val endAt: String,
)

private fun calculateLocalMonthRange(zoneId: ZoneId): MonthlyRange {
    val today = LocalDate.now(zoneId)
    val firstDay = today.withDayOfMonth(1)
    val nextMonthFirstDay = firstDay.plusMonths(1)
    val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    val start = firstDay.atStartOfDay(zoneId).toOffsetDateTime().format(formatter)
    val end = nextMonthFirstDay.atStartOfDay(zoneId).toOffsetDateTime().format(formatter)
    return MonthlyRange(startAt = start, endAt = end)
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
private data class GetIncomeStatementMonthlySummaryPayload(
    @SerialName("p_warehouse_id")
    val warehouseId: Long? = null,
    @SerialName("p_start_at")
    val startAt: String? = null,
    @SerialName("p_end_at")
    val endAt: String? = null,
)

@Serializable
private data class GetDashboardSalesFeedPayload(
    @SerialName("p_warehouse_id")
    val warehouseId: Long? = null,
    @SerialName("p_start_at")
    val startAt: String? = null,
    @SerialName("p_end_at")
    val endAt: String? = null,
    @SerialName("p_limit")
    val limit: Int = 50,
)
