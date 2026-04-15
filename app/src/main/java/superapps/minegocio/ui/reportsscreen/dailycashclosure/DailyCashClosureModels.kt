package superapps.minegocio.ui.reportsscreen.dailycashclosure

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DailyCashClosureWarehouse(
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

@Serializable
data class DailyCashClosureSummary(
    @SerialName("sales_count")
    val salesCount: Int = 0,
    @SerialName("units_sold")
    val unitsSold: Double = 0.0,
    @SerialName("gross_total")
    val grossTotal: Double = 0.0,
    @SerialName("cash_total")
    val cashTotal: Double = 0.0,
    @SerialName("card_total")
    val cardTotal: Double = 0.0,
    @SerialName("transfer_total")
    val transferTotal: Double = 0.0,
    @SerialName("other_total")
    val otherTotal: Double = 0.0,
)
