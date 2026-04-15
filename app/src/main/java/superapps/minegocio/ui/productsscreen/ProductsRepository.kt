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
import java.util.UUID

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

    suspend fun fetchProductOptionsCatalog(): ProductOptionsCatalogResponse = withContext(Dispatchers.IO) {
        SupabaseProvider.assertConfigured()
        val endpoint = "${SupabaseProvider.restUrl}/rpc/get_product_options_catalog"
        val body = "{}"
        val result = post(endpoint, body)
        if (result.code !in 200..299) {
            throw IOException(parseSupabaseError(result.body, "Failed to fetch options catalog (${result.code})"))
        }
        return@withContext json.decodeFromString(result.body)
    }

    suspend fun createProduct(
        payload: CreateProductPayload,
        imageUpload: ProductImageUpload? = null,
    ): Product = withContext(Dispatchers.IO) {
        SupabaseProvider.assertConfigured()
        val payloadWithImage = if (imageUpload != null) {
            payload.copy(imageUrl = uploadProductImage(imageUpload))
        } else {
            payload
        }
        val endpoint = "${SupabaseProvider.restUrl}/rpc/create_product_with_variants"
        val body = json.encodeToString(CreateProductRpcPayload(payload = payloadWithImage))
        val result = post(endpoint, body)
        if (result.code !in 200..299) {
            throw IOException(parseSupabaseError(result.body, "Failed to create product (${result.code})"))
        }
        return@withContext json.decodeFromString(result.body)
    }

    private suspend fun uploadProductImage(imageUpload: ProductImageUpload): String {
        val objectPath = buildStorageObjectPath(imageUpload.fileExtension)
        val endpoint = "${SupabaseProvider.supabaseUrl}/storage/v1/object/$PRODUCT_IMAGES_BUCKET/$objectPath"
        val token = authSessionManager.getSupabaseAccessToken(forceRefresh = false)
        val first = executeBinary(
            endpoint = endpoint,
            accessToken = token,
            bytes = imageUpload.bytes,
            mimeType = imageUpload.mimeType,
        )
        val result = if (first.code == 401) {
            val refreshed = authSessionManager.getSupabaseAccessToken(forceRefresh = true)
            executeBinary(
                endpoint = endpoint,
                accessToken = refreshed,
                bytes = imageUpload.bytes,
                mimeType = imageUpload.mimeType,
            )
        } else {
            first
        }
        if (result.code !in 200..299) {
            throw IOException(parseSupabaseError(result.body, "Failed to upload product image (${result.code})"))
        }
        return "${SupabaseProvider.supabaseUrl}/storage/v1/object/public/$PRODUCT_IMAGES_BUCKET/$objectPath"
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

    private fun executeBinary(
        endpoint: String,
        accessToken: String,
        bytes: ByteArray,
        mimeType: String,
    ): HttpResult {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10000
            readTimeout = 15000
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("apikey", SupabaseProvider.anonKey)
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("x-upsert", "true")
            setRequestProperty("Content-Type", mimeType)
        }
        try {
            connection.outputStream.use { out -> out.write(bytes) }
            val code = connection.responseCode
            val responseBody = readBody(connection, code in 200..299)
            return HttpResult(code, responseBody)
        } finally {
            connection.disconnect()
        }
    }

    private fun buildStorageObjectPath(fileExtension: String): String {
        val sanitizedExtension = fileExtension.lowercase().ifBlank { "jpg" }
        return "products/${System.currentTimeMillis()}-${UUID.randomUUID()}.$sanitizedExtension"
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

private const val PRODUCT_IMAGES_BUCKET = "product-images"

