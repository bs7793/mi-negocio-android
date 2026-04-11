package superapps.minegocio.ui.productsscreen

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import superapps.minegocio.ui.auth.AuthSessionManager
import superapps.minegocio.ui.categoriesscreen.Category
import superapps.minegocio.ui.categoriesscreen.SupabaseProvider
import superapps.minegocio.ui.warehousesscreen.Warehouse
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class ProductsRepository(
    private val authSessionManager: AuthSessionManager = AuthSessionManager(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchProducts(
        limit: Int = 20,
        offset: Int = 0,
        search: String? = null,
        categoryId: Long? = null,
        warehouseId: Long? = null,
    ): ProductsListResponse = withContext(Dispatchers.IO) {
        SupabaseProvider.assertConfigured()
        val endpoint = "${SupabaseProvider.restUrl}/rpc/get_products_list"
        val payload = GetProductsListRpcPayload(
            limit = limit,
            offset = offset,
            search = search?.trim().takeUnless { it.isNullOrBlank() },
            categoryId = categoryId,
            warehouseId = warehouseId,
        )
        val body = json.encodeToString(payload)
        val result = post(endpoint, body)
        if (result.code !in 200..299) {
            throw IOException(parseSupabaseError(result.body, "Failed to fetch products (${result.code})"))
        }
        return@withContext json.decodeFromString(result.body)
    }

    suspend fun fetchCategories(): List<Category> = withContext(Dispatchers.IO) {
        SupabaseProvider.assertConfigured()
        val endpoint = "${SupabaseProvider.restUrl}/categories?select=id,workspace_id,name,description&order=name.asc"
        val result = get(endpoint)
        if (result.code !in 200..299) {
            throw IOException(parseSupabaseError(result.body, "Failed to fetch categories (${result.code})"))
        }
        return@withContext json.decodeFromString(result.body)
    }

    suspend fun fetchWarehouses(): List<Warehouse> = withContext(Dispatchers.IO) {
        SupabaseProvider.assertConfigured()
        val endpoint = "${SupabaseProvider.restUrl}/warehouses?select=id,workspace_id,name,location,aisle,shelf,level,position&order=name.asc"
        val result = get(endpoint)
        if (result.code !in 200..299) {
            throw IOException(parseSupabaseError(result.body, "Failed to fetch warehouses (${result.code})"))
        }
        return@withContext json.decodeFromString(result.body)
    }

    suspend fun createProduct(payload: CreateProductPayload): Product = withContext(Dispatchers.IO) {
        SupabaseProvider.assertConfigured()
        val endpoint = "${SupabaseProvider.restUrl}/rpc/create_product_with_variants"
        val body = json.encodeToString(CreateProductRpcPayload(payload = payload))
        val result = post(endpoint, body)
        if (result.code !in 200..299) {
            throw IOException(parseSupabaseError(result.body, "Failed to create product (${result.code})"))
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

@Serializable
private data class GetProductsListRpcPayload(
    @kotlinx.serialization.SerialName("p_limit")
    val limit: Int,
    @kotlinx.serialization.SerialName("p_offset")
    val offset: Int,
    @kotlinx.serialization.SerialName("p_search")
    val search: String? = null,
    @kotlinx.serialization.SerialName("p_category_id")
    val categoryId: Long? = null,
    @kotlinx.serialization.SerialName("p_warehouse_id")
    val warehouseId: Long? = null,
)

@Serializable
private data class CreateProductRpcPayload(
    @kotlinx.serialization.SerialName("p_payload")
    val payload: CreateProductPayload,
)

