package superapps.minegocio.ui.warehousesscreen

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Warehouse(
    val id: Long,
    @SerialName("workspace_id")
    val workspaceId: String,
    val name: String,
    val location: String? = null,
    val aisle: String? = null,
    val shelf: String? = null,
    val level: String? = null,
    @SerialName("position")
    val position: String? = null,
)
