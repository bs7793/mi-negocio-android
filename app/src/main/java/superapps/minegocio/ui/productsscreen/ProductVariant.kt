package superapps.minegocio.ui.productsscreen

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProductVariant(
    @SerialName("variant_id")
    val variantId: Long,
    val sku: String,
    val barcode: String? = null,
    @SerialName("unit_price")
    val unitPrice: Double,
    @SerialName("cost_price")
    val costPrice: Double? = null,
    @SerialName("stock_total")
    val stockTotal: Double = 0.0,
    val options: List<ProductOption> = emptyList(),
)

@Serializable
data class VariantInventoryInput(
    @SerialName("warehouse_id")
    val warehouseId: Long,
    val quantity: Double,
    @SerialName("reorder_level")
    val reorderLevel: Double? = null,
    @SerialName("alert_active")
    val alertActive: Boolean = false,
    @SerialName("alert_method")
    val alertMethod: String? = null,
)

@Serializable
data class ProductOptionInput(
    @SerialName("type")
    val type: String,
    @SerialName("value")
    val value: String,
)

@Serializable
data class ProductVariantInput(
    val sku: String,
    val barcode: String? = null,
    @SerialName("unit_price")
    val unitPrice: Double,
    @SerialName("cost_price")
    val costPrice: Double? = null,
    @SerialName("option_values")
    val optionValues: List<ProductOptionInput> = emptyList(),
    val inventory: List<VariantInventoryInput>,
)

