package superapps.minegocio.ui.dashboardscreen

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DashboardWarehouse(
    val id: Long,
    @SerialName("workspace_id")
    val workspaceId: String,
    val name: String,
)

@Serializable
data class DashboardIncomeStatementSummary(
    @SerialName("income_total")
    val incomeTotal: Double = 0.0,
    @SerialName("cost_total")
    val costTotal: Double = 0.0,
    @SerialName("profit_total")
    val profitTotal: Double = 0.0,
)
