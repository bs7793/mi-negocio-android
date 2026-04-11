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

