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
import superapps.minegocio.ui.workspacesession.WorkspaceSelectionStore
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
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
        val workspaceId = requireSelectedWorkspaceId()
        val endpoint = "${SupabaseProvider.restUrl}/rpc/get_products_list"
        val payload = GetProductsListRpcPayload(
            limit = limit,
            offset = offset,
            search = search?.trim().takeUnless { it.isNullOrBlank() },
            categoryId = categoryId,
            warehouseId = warehouseId,
            workspaceId = workspaceId,
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
        val workspaceId = requireSelectedWorkspaceId()
        val endpoint =
            "${SupabaseProvider.restUrl}/categories" +
                "?select=id,workspace_id,name,description" +
                "&workspace_id=eq.${workspaceId.urlEncode()}" +
                "&order=name.asc"
        val result = get(endpoint)
        if (result.code !in 200..299) {
            throw IOException(parseSupabaseError(result.body, "Failed to fetch categories (${result.code})"))
        }
        return@withContext json.decodeFromString(result.body)
    }

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

    suspend fun fetchProductOptionsCatalog(): ProductOptionsCatalogResponse = withContext(Dispatchers.IO) {
        SupabaseProvider.assertConfigured()
        val workspaceId = requireSelectedWorkspaceId()
        val endpoint = "${SupabaseProvider.restUrl}/rpc/get_product_options_catalog"
        val body = json.encodeToString(GetProductOptionsCatalogPayload(workspaceId = workspaceId))
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
        val workspaceId = requireSelectedWorkspaceId()
        val payloadWithImage = if (imageUpload != null) {
            payload.copy(imageUrl = uploadProductImage(imageUpload))
        } else {
            payload
        }
        val workspaceScopedPayload = payloadWithImage.copy(
            workspaceId = payloadWithImage.workspaceId ?: workspaceId,
        )
        val endpoint = "${SupabaseProvider.restUrl}/rpc/create_product_with_variants"
        val body = json.encodeToString(CreateProductRpcPayload(payload = workspaceScopedPayload))
        val result = post(endpoint, body)
        if (result.code !in 200..299) {
            throw IOException(parseSupabaseError(result.body, "Failed to create product (${result.code})"))
        }
        return@withContext json.decodeFromString(result.body)
    }

    suspend fun updateProductBasic(
        payload: UpdateProductBasicPayload,
        imageUpload: ProductImageUpload? = null,
        previousImageUrl: String? = null,
    ): UpdateProductBasicResult = withContext(Dispatchers.IO) {
        SupabaseProvider.assertConfigured()
        val workspaceId = requireSelectedWorkspaceId()
        val payloadWithImage = if (imageUpload != null) {
            payload.copy(imageUrl = uploadProductImage(imageUpload))
        } else {
            payload
        }
        val workspaceScopedPayload = payloadWithImage.copy(
            workspaceId = payloadWithImage.workspaceId ?: workspaceId,
        )
        val endpoint = "${SupabaseProvider.restUrl}/rpc/update_product_basic"
        val body = json.encodeToString(UpdateProductBasicRpcPayload(payload = workspaceScopedPayload))
        val result = post(endpoint, body)
        if (result.code !in 200..299) {
            throw IOException(parseSupabaseError(result.body, "Failed to update product (${result.code})"))
        }
        val updatedProduct: Product = json.decodeFromString(result.body)
        val cleanupWarning = cleanupPreviousImageIfNeeded(
            previousImageUrl = previousImageUrl,
            nextImageUrl = workspaceScopedPayload.imageUrl,
        )
        return@withContext UpdateProductBasicResult(
            product = updatedProduct,
            cleanupWarning = cleanupWarning,
        )
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

    private fun requireSelectedWorkspaceId(): String {
        return WorkspaceSelectionStore.selectedWorkspaceId
            ?.takeIf { it.isNotBlank() }
            ?: throw IOException("Selecciona un workspace antes de continuar.")
    }

    private suspend fun post(endpoint: String, body: String): HttpResult {
        val token = authSessionManager.getSupabaseAccessToken(forceRefresh = false)
        val first = execute(endpoint, "POST", token, body)
        if (first.code != 401) return first
        val refreshed = authSessionManager.getSupabaseAccessToken(forceRefresh = true)
        return execute(endpoint, "POST", refreshed, body)
    }

    private suspend fun delete(endpoint: String): HttpResult {
        val token = authSessionManager.getSupabaseAccessToken(forceRefresh = false)
        val first = execute(endpoint, "DELETE", token, null)
        if (first.code != 401) return first
        val refreshed = authSessionManager.getSupabaseAccessToken(forceRefresh = true)
        return execute(endpoint, "DELETE", refreshed, null)
    }

    private suspend fun cleanupPreviousImageIfNeeded(
        previousImageUrl: String?,
        nextImageUrl: String?,
    ): String? {
        if (!shouldDeletePreviousImage(previousImageUrl, nextImageUrl)) return null
        val previousUrl = previousImageUrl.orEmpty().trim()
        val objectPath = extractStorageObjectPath(previousUrl) ?: return CLEANUP_WARNING_MESSAGE
        val endpoint = "${SupabaseProvider.supabaseUrl}/storage/v1/object/$PRODUCT_IMAGES_BUCKET/$objectPath"
        val result = delete(endpoint)
        if (result.code in 200..299 || result.code == 404) return null
        return parseSupabaseError(result.body, CLEANUP_WARNING_MESSAGE)
    }

    private fun shouldDeletePreviousImage(previousImageUrl: String?, nextImageUrl: String?): Boolean {
        val previous = previousImageUrl?.trim().orEmpty()
        if (previous.isBlank()) return false
        val next = nextImageUrl?.trim().orEmpty()
        return !previous.equals(next, ignoreCase = true)
    }

    private fun extractStorageObjectPath(publicUrl: String): String? {
        if (publicUrl.isBlank()) return null
        val prefix = "${SupabaseProvider.supabaseUrl}/storage/v1/object/public/$PRODUCT_IMAGES_BUCKET/"
        if (!publicUrl.startsWith(prefix)) return null
        val rawPath = publicUrl.removePrefix(prefix).trim()
        return rawPath.takeIf { it.isNotBlank() }
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

data class UpdateProductBasicResult(
    val product: Product,
    val cleanupWarning: String? = null,
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
    @kotlinx.serialization.SerialName("p_workspace_id")
    val workspaceId: String,
)

@Serializable
private data class GetProductOptionsCatalogPayload(
    @kotlinx.serialization.SerialName("p_workspace_id")
    val workspaceId: String,
)

@Serializable
private data class CreateProductRpcPayload(
    @kotlinx.serialization.SerialName("p_payload")
    val payload: CreateProductPayload,
)

@Serializable
private data class UpdateProductBasicRpcPayload(
    @kotlinx.serialization.SerialName("p_payload")
    val payload: UpdateProductBasicPayload,
)

private const val PRODUCT_IMAGES_BUCKET = "product-images"
private const val CLEANUP_WARNING_MESSAGE = "Product updated, but old image cleanup is pending."

private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.toString())

