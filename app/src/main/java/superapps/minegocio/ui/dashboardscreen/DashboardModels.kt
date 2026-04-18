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

@Serializable
data class DashboardSalesFeedItem(
    @SerialName("sale_id")
    val saleId: Long,
    @SerialName("sold_at")
    val soldAt: String,
    val total: Double = 0.0,
    @SerialName("customer_name")
    val customerName: String? = null,
    @SerialName("payment_method")
    val paymentMethod: String? = null,
)

@Serializable
data class DashboardSaleDetail(
    @SerialName("sale_id")
    val saleId: Long,
    @SerialName("sold_at")
    val soldAt: String,
    @SerialName("customer_name")
    val customerName: String? = null,
    val notes: String? = null,
    val subtotal: Double = 0.0,
    @SerialName("discount_total")
    val discountTotal: Double = 0.0,
    @SerialName("tax_total")
    val taxTotal: Double = 0.0,
    val total: Double = 0.0,
    @SerialName("paid_total")
    val paidTotal: Double = 0.0,
    @SerialName("change_total")
    val changeTotal: Double = 0.0,
    val status: String = "completed",
    val lines: List<DashboardSaleDetailLine> = emptyList(),
    val payments: List<DashboardSaleDetailPayment> = emptyList(),
)

@Serializable
data class DashboardSaleDetailLine(
    @SerialName("variant_id")
    val variantId: Long,
    @SerialName("product_name")
    val productName: String,
    val sku: String,
    val quantity: Double = 0.0,
    @SerialName("applied_unit_price")
    val appliedUnitPrice: Double = 0.0,
    @SerialName("line_total")
    val lineTotal: Double = 0.0,
    val notes: String? = null,
)

@Serializable
data class DashboardSaleDetailPayment(
    @SerialName("payment_method")
    val paymentMethod: String,
    val amount: Double = 0.0,
    @SerialName("reference_text")
    val referenceText: String? = null,
)
