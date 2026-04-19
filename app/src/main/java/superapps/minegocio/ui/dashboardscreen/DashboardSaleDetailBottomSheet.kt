package superapps.minegocio.ui.dashboardscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import superapps.minegocio.R
import java.text.NumberFormat
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardSaleDetailBottomSheet(
    onDismissRequest: () -> Unit,
    isLoading: Boolean,
    detail: DashboardSaleDetail?,
    errorMessage: String?,
    onRetry: () -> Unit,
    isReceiptGenerating: Boolean,
    receiptErrorMessage: String?,
    onCreateReceipt: () -> Unit,
    onDismissReceiptError: () -> Unit,
    amountFormatter: NumberFormat,
    locale: Locale,
) {
    val qtyFormatter = remember(locale) {
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 3
        }
    }

    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.dashboard_sale_detail_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            when {
                isLoading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }

                errorMessage != null -> {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Button(onClick = onRetry) {
                                Text(stringResource(R.string.dashboard_sale_detail_retry))
                            }
                        }
                    }
                }

                detail != null -> {
                    item {
                        Text(
                            text = stringResource(R.string.dashboard_sale_detail_id, detail.saleId),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    item {
                        Button(
                            onClick = onCreateReceipt,
                            enabled = !isReceiptGenerating,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (isReceiptGenerating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Text(
                                    text = stringResource(R.string.dashboard_receipt_creating),
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            } else {
                                Text(text = stringResource(R.string.dashboard_receipt_create))
                            }
                        }
                    }

                    if (receiptErrorMessage != null) {
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.errorContainer,
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.dashboard_receipt_error_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = receiptErrorMessage.ifBlank {
                                            stringResource(R.string.dashboard_receipt_error_text)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = onDismissReceiptError) {
                                            Text(stringResource(R.string.dashboard_receipt_dismiss))
                                        }
                                        Button(onClick = onCreateReceipt, enabled = !isReceiptGenerating) {
                                            Text(stringResource(R.string.dashboard_receipt_retry))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = stringResource(R.string.dashboard_sale_detail_section_summary),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val soldAtLabel = remember(detail.soldAt, locale) {
                                formatSoldAtForDetail(detail.soldAt, locale)
                            }
                            DetailMoneyRow(
                                label = stringResource(R.string.dashboard_sale_detail_sold_at),
                                value = soldAtLabel,
                            )
                            detail.customerName?.takeIf { it.isNotBlank() }?.let { name ->
                                DetailMoneyRow(
                                    label = stringResource(R.string.dashboard_sale_detail_customer),
                                    value = name,
                                )
                            }
                            DetailMoneyRow(
                                label = stringResource(R.string.dashboard_sale_detail_status),
                                value = saleStatusLabel(detail.status),
                            )
                            DetailMoneyRow(
                                label = stringResource(R.string.dashboard_sale_detail_subtotal),
                                value = amountFormatter.format(detail.subtotal),
                            )
                            DetailMoneyRow(
                                label = stringResource(R.string.dashboard_sale_detail_discount),
                                value = amountFormatter.format(detail.discountTotal),
                            )
                            DetailMoneyRow(
                                label = stringResource(R.string.dashboard_sale_detail_tax),
                                value = amountFormatter.format(detail.taxTotal),
                            )
                            DetailMoneyRow(
                                label = stringResource(R.string.dashboard_sale_detail_total),
                                value = amountFormatter.format(detail.total),
                                emphasizeValue = true,
                            )
                            DetailMoneyRow(
                                label = stringResource(R.string.dashboard_sale_detail_paid),
                                value = amountFormatter.format(detail.paidTotal),
                            )
                            DetailMoneyRow(
                                label = stringResource(R.string.dashboard_sale_detail_change),
                                value = amountFormatter.format(detail.changeTotal),
                            )
                        }
                    }

                    item {
                        Text(
                            text = stringResource(R.string.dashboard_sale_detail_section_items),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    items(
                        items = detail.lines,
                        key = { it.variantId },
                    ) { line ->
                        SaleDetailLineCard(
                            line = line,
                            amountFormatter = amountFormatter,
                            qtyFormatter = qtyFormatter,
                        )
                    }

                    item {
                        Text(
                            text = stringResource(R.string.dashboard_sale_detail_section_payments),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    items(
                        count = detail.payments.size,
                        key = { index -> "payment_$index" },
                    ) { index ->
                        val payment = detail.payments[index]
                        SaleDetailPaymentRow(
                            payment = payment,
                            amountFormatter = amountFormatter,
                        )
                    }

                    if (!detail.notes.isNullOrBlank()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = stringResource(R.string.dashboard_sale_detail_notes),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = detail.notes,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailMoneyRow(
    label: String,
    value: String,
    emphasizeValue: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = if (emphasizeValue) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyMedium
            },
            fontWeight = if (emphasizeValue) FontWeight.Bold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SaleDetailLineCard(
    line: DashboardSaleDetailLine,
    amountFormatter: NumberFormat,
    qtyFormatter: NumberFormat,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            val imageModifier = Modifier
                .size(72.dp)
                .clip(MaterialTheme.shapes.medium)
            val imageDescription = stringResource(
                R.string.dashboard_sale_detail_line_image_a11y,
                line.productName,
            )
            if (!line.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = line.imageUrl,
                    contentDescription = imageDescription,
                    modifier = imageModifier,
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = imageModifier.background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Image,
                        contentDescription = imageDescription,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = line.productName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.dashboard_sale_detail_line_meta,
                        line.sku,
                        qtyFormatter.format(line.quantity),
                        amountFormatter.format(line.appliedUnitPrice),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                line.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = stringResource(
                        R.string.dashboard_sale_detail_line_total,
                        amountFormatter.format(line.lineTotal),
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun SaleDetailPaymentRow(
    payment: DashboardSaleDetailPayment,
    amountFormatter: NumberFormat,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = paymentMethodLabelForDetail(payment.paymentMethod),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = amountFormatter.format(payment.amount),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        payment.referenceText?.takeIf { it.isNotBlank() }?.let { ref ->
            Text(
                text = stringResource(R.string.dashboard_sale_detail_payment_ref, ref),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun formatSoldAtForDetail(soldAt: String, locale: Locale): String {
    return try {
        val odt = OffsetDateTime.parse(soldAt)
        DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale)
            .format(odt)
    } catch (_: Exception) {
        soldAt
    }
}

@Composable
private fun saleStatusLabel(status: String): String {
    return when (status.lowercase(Locale.ROOT)) {
        "draft" -> stringResource(R.string.dashboard_sale_status_draft)
        "completed" -> stringResource(R.string.dashboard_sale_status_completed)
        "voided" -> stringResource(R.string.dashboard_sale_status_voided)
        else -> status
    }
}

@Composable
private fun paymentMethodLabelForDetail(method: String): String {
    return when (method.lowercase(Locale.ROOT)) {
        "cash" -> stringResource(R.string.sales_payment_cash)
        "card" -> stringResource(R.string.sales_payment_card)
        "transfer" -> stringResource(R.string.sales_payment_transfer)
        "other" -> stringResource(R.string.sales_payment_other)
        else -> method
    }
}
