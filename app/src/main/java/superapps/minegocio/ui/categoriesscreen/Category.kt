package superapps.minegocio.ui.categoriesscreen

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class Category(
    val id: Long,
    @SerialName("workspace_id")
    val workspaceId: String,
    val name: String,
    val description: String? = null,
)
