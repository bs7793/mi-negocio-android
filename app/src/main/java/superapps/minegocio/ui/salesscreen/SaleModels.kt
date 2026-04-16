package superapps.minegocio.ui.salesscreen

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SellableVariant(
    @SerialName("variant_id")
    val variantId: Long,
    @SerialName("product_id")
    val productId: Long,
    @SerialName("product_name")
    val productName: String,
    @SerialName("image_url")
    val imageUrl: String? = null,
    val sku: String,
    val barcode: String? = null,
    @SerialName("unit_price")
    val unitPrice: Double,
    @SerialName("cost_price")
    val costPrice: Double? = null,
    @SerialName("stock_total")
    val stockTotal: Double = 0.0,
    val options: List<SellableVariantOption> = emptyList(),
)

@Serializable
data class SellableVariantOption(
    val type: String,
    val value: String,
)

@Serializable
data class SellableVariantsResponse(
    val items: List<SellableVariant> = emptyList(),
)

data class SaleCartItem(
    val variant: SellableVariant,
    val quantityInput: String = "1",
    val unitPriceInput: String = variant.unitPrice.toString(),
    val notes: String = "",
) {
    val quantity: Double
        get() = quantityInput.toDoubleOrNull()?.takeIf { it > 0 } ?: 0.0

    val unitPrice: Double
        get() = unitPriceInput.toDoubleOrNull()?.takeIf { it >= 0 } ?: 0.0

    val lineTotal: Double
        get() = quantity * unitPrice
}

data class SalesPaymentDraft(
    val method: String = "cash",
    val amountInput: String = "",
    val reference: String = "",
) {
    val amount: Double
        get() = amountInput.toDoubleOrNull()?.takeIf { it > 0 } ?: 0.0
}

@Serializable
data class SaleCreateResponse(
    @SerialName("sale_id")
    val saleId: Long,
    val total: Double,
    @SerialName("paid_total")
    val paidTotal: Double,
    @SerialName("change_total")
    val changeTotal: Double,
)

@Serializable
data class SalesDailySummary(
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
