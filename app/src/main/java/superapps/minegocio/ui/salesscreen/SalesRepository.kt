package superapps.minegocio.ui.salesscreen

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import superapps.minegocio.ui.auth.AuthSessionManager
import superapps.minegocio.ui.categoriesscreen.SupabaseProvider
import superapps.minegocio.ui.warehousesscreen.Warehouse
import superapps.minegocio.ui.workspacesession.WorkspaceSelectionStore
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets

class SalesRepository(
    private val authSessionManager: AuthSessionManager = AuthSessionManager(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchWarehouses(): List<Warehouse> = withContext(Dispatchers.IO) {
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

    suspend fun fetchSellableVariants(
        search: String?,
        warehouseId: Long?,
        limit: Int = 30,
    ): SellableVariantsResponse = withContext(Dispatchers.IO) {
        SupabaseProvider.assertConfigured()
        val workspaceId = requireSelectedWorkspaceId()
        val endpoint = "${SupabaseProvider.restUrl}/rpc/get_sellable_variants"
        val payload = GetSellableVariantsPayload(
            search = search?.trim().takeUnless { it.isNullOrBlank() },
            limit = limit,
            warehouseId = warehouseId,
            workspaceId = workspaceId,
        )
        val body = json.encodeToString(payload)
        val result = post(endpoint, body)
        if (result.code !in 200..299) {
            throw IOException(parseSupabaseError(result.body, "Failed to fetch variants (${result.code})"))
        }
        return@withContext json.decodeFromString(result.body)
    }

    suspend fun createSale(
        warehouseId: Long,
        customerName: String?,
        notes: String?,
        lines: List<SaleCreateLineInput>,
        payments: List<SaleCreatePaymentInput>,
    ): SaleCreateResponse = withContext(Dispatchers.IO) {
        SupabaseProvider.assertConfigured()
        val workspaceId = requireSelectedWorkspaceId()
        val endpoint = "${SupabaseProvider.restUrl}/rpc/create_sale_with_lines_and_payments"
        val payload = CreateSaleRpcPayload(
            payload = CreateSalePayload(
                workspaceId = workspaceId,
                warehouseId = warehouseId,
                customerName = customerName?.trim().takeUnless { it.isNullOrBlank() },
                notes = notes?.trim().takeUnless { it.isNullOrBlank() },
                lines = lines,
                payments = payments,
            ),
        )
        val body = json.encodeToString(payload)
        val result = post(endpoint, body)
        if (result.code !in 200..299) {
            throw IOException(parseSupabaseError(result.body, "Failed to create sale (${result.code})"))
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

@Serializable
data class SaleCreateLineInput(
    @SerialName("variant_id")
    val variantId: Long,
    val quantity: Double,
    @SerialName("applied_unit_price")
    val appliedUnitPrice: Double,
    @SerialName("applied_cost_price")
    val appliedCostPrice: Double? = null,
    val notes: String? = null,
)

@Serializable
data class SaleCreatePaymentInput(
    @SerialName("method")
    val method: String,
    val amount: Double,
    @SerialName("reference")
    val reference: String? = null,
)

private data class HttpResult(
    val code: Int,
    val body: String,
)

private fun readBody(connection: HttpURLConnection, isSuccess: Boolean): String {
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

@Serializable
private data class GetSellableVariantsPayload(
    @SerialName("p_search")
    val search: String? = null,
    @SerialName("p_limit")
    val limit: Int = 30,
    @SerialName("p_warehouse_id")
    val warehouseId: Long? = null,
    @SerialName("p_workspace_id")
    val workspaceId: String,
)

@Serializable
private data class CreateSaleRpcPayload(
    @SerialName("p_payload")
    val payload: CreateSalePayload,
)

@Serializable
private data class CreateSalePayload(
    @SerialName("workspace_id")
    val workspaceId: String? = null,
    @SerialName("warehouse_id")
    val warehouseId: Long,
    @SerialName("customer_name")
    val customerName: String? = null,
    val notes: String? = null,
    val lines: List<SaleCreateLineInput>,
    val payments: List<SaleCreatePaymentInput>,
)

private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.toString())
