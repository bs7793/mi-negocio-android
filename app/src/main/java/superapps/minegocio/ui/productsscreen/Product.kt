package superapps.minegocio.ui.productsscreen

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Product(
    @SerialName("product_id")
    val productId: Long,
    val name: String,
    val description: String? = null,
    @SerialName("image_url")
    val imageUrl: String? = null,
    @SerialName("category_id")
    val categoryId: Long? = null,
    @SerialName("category_name")
    val categoryName: String? = null,
    @SerialName("total_stock")
    val totalStock: Double = 0.0,
    @SerialName("variants_count")
    val variantsCount: Int = 0,
    val variants: List<ProductVariant> = emptyList(),
)

@Serializable
data class ProductsListResponse(
    val total: Int = 0,
    val limit: Int = 20,
    val offset: Int = 0,
    val items: List<Product> = emptyList(),
)

@Serializable
data class CreateProductPayload(
    val name: String,
    val description: String? = null,
    @SerialName("image_url")
    val imageUrl: String? = null,
    @SerialName("category_id")
    val categoryId: Long? = null,
    val variants: List<ProductVariantInput>,
)

@Serializable
data class UpdateProductBasicPayload(
    @SerialName("product_id")
    val productId: Long,
    val name: String,
    val description: String?,
    @SerialName("image_url")
    val imageUrl: String?,
    @SerialName("category_id")
    val categoryId: Long?,
)

data class ProductImageUpload(
    val bytes: ByteArray,
    val mimeType: String,
    val fileExtension: String,
)
