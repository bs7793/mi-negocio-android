package superapps.minegocio.ui.productsscreen

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProductOption(
    @SerialName("type")
    val type: String,
    @SerialName("value")
    val value: String,
)

@Serializable
data class ProductOptionValueCatalog(
    val id: Long,
    val value: String,
    @SerialName("sort_order")
    val sortOrder: Int = 0,
)

@Serializable
data class ProductOptionTypeCatalog(
    val id: Long,
    val name: String,
    @SerialName("input_kind")
    val inputKind: String = "text",
    val values: List<ProductOptionValueCatalog> = emptyList(),
)

@Serializable
data class ProductOptionsCatalogResponse(
    @SerialName("option_types")
    val optionTypes: List<ProductOptionTypeCatalog> = emptyList(),
)

