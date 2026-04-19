package superapps.minegocio.ui.dashboardscreen

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

class DashboardRepository(
    private val authSessionManager: AuthSessionManager = AuthSessionManager(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var cachedWorkspaceId: String? = null
    private var cachedWorkspaceForUserId: String? = null

    suspend fun fetchWarehouses(): List<DashboardWarehouse> = withContext(Dispatchers.IO) {
        SupabaseProvider.assertConfigured()
        val workspaceId = primaryWorkspaceId()
        val endpoint =
            "${SupabaseProvider.restUrl}/warehouses" +
                "?select=id,workspace_id,name" +
                "&workspace_id=eq.${workspaceId.urlEncode()}" +
                "&order=name.asc"
        val result = get(endpoint)
        if (result.code !in 200..299) {
            throw IOException(parseSupabaseError(result.body, "Failed to fetch warehouses (${result.code})"))
        }
        return@withContext json.decodeFromString(result.body)
    }

    private suspend fun primaryWorkspaceId(): String {
        val uid = authSessionManager.currentUserIdOrNull()
        val selectedWorkspaceId = WorkspaceSelectionStore.selectedWorkspaceId
        if (!selectedWorkspaceId.isNullOrBlank()) {
            cachedWorkspaceId = selectedWorkspaceId
            cachedWorkspaceForUserId = uid
            return selectedWorkspaceId
        }
        if (cachedWorkspaceId != null && uid != null && cachedWorkspaceForUserId == uid) {
            return cachedWorkspaceId!!
        }
        val endpoint = "${SupabaseProvider.restUrl}/rpc/get_my_primary_workspace_id"
        val result = post(endpoint, "{}")
        if (result.code !in 200..299) {
            throw IOException(parseSupabaseError(result.body, "Failed to resolve workspace (${result.code})"))
        }
        val id = result.body.trim().removePrefix("\"").removeSuffix("\"")
        if (id.isBlank()) throw IOException("Workspace id response was empty")
        cachedWorkspaceId = id
        cachedWorkspaceForUserId = uid
        return id
    }

    suspend fun fetchSaleDetail(saleId: Long): DashboardSaleDetail = withContext(Dispatchers.IO) {
        SupabaseProvider.assertConfigured()
        val endpoint = "${SupabaseProvider.restUrl}/rpc/get_dashboard_sale_detail"
        val body = json.encodeToString(GetDashboardSaleDetailPayload(saleId = saleId))
        val result = post(endpoint, body)
        if (result.code !in 200..299) {
            throw IOException(parseSupabaseError(result.body, "Failed to fetch sale detail (${result.code})"))
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

    suspend fun createSaleReceipt(saleId: Long): String = withContext(Dispatchers.IO) {
        SupabaseProvider.assertConfigured()
        val endpoint = "${SupabaseProvider.functionsUrl}/generate-sale-receipt"
        val body = json.encodeToString(CreateSaleReceiptRequest(saleId = saleId))
        val result = post(endpoint, body)
        if (result.code !in 200..299) {
            throw IOException(parseSupabaseError(result.body, "Failed to generate sale receipt (${result.code})"))
        }
        val payload = runCatching { json.decodeFromString<DashboardReceiptSharePayload>(result.body) }.getOrNull()
        val receiptUrl = payload?.shareUrl ?: payload?.receiptUrl ?: payload?.legacyUrl
        if (receiptUrl.isNullOrBlank()) {
            throw IOException("Receipt generated but no share URL was returned")
        }
        return@withContext receiptUrl
    }

    private suspend fun get(endpoint: String): HttpResult {
        val token = authSessionManager.getSupabaseAccessTokenOrNull(forceRefresh = false)
            ?: return HttpResult(401, """{"message":"No active Supabase session"}""")
        val first = execute(endpoint, "GET", token, null)
        if (first.code != 401) return first
        val refreshed = authSessionManager.getSupabaseAccessTokenOrNull(forceRefresh = true)
            ?: return HttpResult(401, """{"message":"No active Supabase session"}""")
        return execute(endpoint, "GET", refreshed, null)
    }

    private suspend fun post(endpoint: String, body: String): HttpResult {
        val token = authSessionManager.getSupabaseAccessTokenOrNull(forceRefresh = false)
            ?: return HttpResult(401, """{"message":"No active Supabase session"}""")
        val first = execute(endpoint, "POST", token, body)
        if (first.code != 401) return first
        val refreshed = authSessionManager.getSupabaseAccessTokenOrNull(forceRefresh = true)
            ?: return HttpResult(401, """{"message":"No active Supabase session"}""")
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

private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.toString())

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

@Serializable
private data class GetDashboardSaleDetailPayload(
    @SerialName("p_sale_id")
    val saleId: Long,
)

@Serializable
private data class CreateSaleReceiptRequest(
    @SerialName("sale_id")
    val saleId: Long,
)

