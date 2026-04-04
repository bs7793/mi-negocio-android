package superapps.minegocio.data

import kotlinx.serialization.Serializable

@Serializable
data class Category(
    val id: Long,
    val name: String,
    val description: String? = null,
)
