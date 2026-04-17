package superapps.minegocio.ui.salesscreen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import superapps.minegocio.R

@Composable
fun PaymentMethodSheet(
    selectedMethod: String,
    onSelectMethod: (String) -> Unit,
    referenceInput: String,
    onReferenceChange: (String) -> Unit,
    showReferenceField: Boolean,
    onToggleReference: () -> Unit,
    cartTotal: Double,
    isSubmitting: Boolean,
    errorMessage: String?,
    onCharge: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.sales_payment_sheet_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.sales_payment_sheet_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PaymentMethodGrid(
            selectedMethod = selectedMethod,
            onSelectMethod = onSelectMethod,
        )

        TextButton(onClick = onToggleReference) {
            Text(stringResource(R.string.sales_add_reference_action))
        }

        if (showReferenceField) {
            OutlinedTextField(
                value = referenceInput,
                onValueChange = onReferenceChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.sales_payment_reference_label)) },
                singleLine = true,
            )
        }

        if (!errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Button(
            onClick = onCharge,
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (isSubmitting) {
                    stringResource(R.string.sales_checkout_processing)
                } else {
                    stringResource(R.string.sales_charge_action, cartTotal)
                },
            )
        }
    }
}

@Composable
private fun PaymentMethodGrid(
    selectedMethod: String,
    onSelectMethod: (String) -> Unit,
) {
    val options = listOf(
        PaymentMethodOption("cash", stringRes = R.string.sales_payment_cash, icon = Icons.Default.AttachMoney),
        PaymentMethodOption("card", stringRes = R.string.sales_payment_card, icon = Icons.Default.CreditCard),
        PaymentMethodOption("transfer", stringRes = R.string.sales_payment_transfer, icon = Icons.Default.SwapHoriz),
        PaymentMethodOption("other", stringRes = R.string.sales_payment_other, icon = Icons.Default.MoreHoriz),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PaymentMethodCard(
                modifier = Modifier.weight(1f),
                option = options[0],
                selected = selectedMethod == options[0].method,
                onSelectMethod = onSelectMethod,
            )
            PaymentMethodCard(
                modifier = Modifier.weight(1f),
                option = options[1],
                selected = selectedMethod == options[1].method,
                onSelectMethod = onSelectMethod,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PaymentMethodCard(
                modifier = Modifier.weight(1f),
                option = options[2],
                selected = selectedMethod == options[2].method,
                onSelectMethod = onSelectMethod,
            )
            PaymentMethodCard(
                modifier = Modifier.weight(1f),
                option = options[3],
                selected = selectedMethod == options[3].method,
                onSelectMethod = onSelectMethod,
            )
        }
    }
}

@Composable
private fun PaymentMethodCard(
    modifier: Modifier = Modifier,
    option: PaymentMethodOption,
    selected: Boolean,
    onSelectMethod: (String) -> Unit,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Card(
        modifier = modifier
            .heightIn(min = 96.dp)
            .clickable { onSelectMethod(option.method) },
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = option.icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = stringResource(option.stringRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private data class PaymentMethodOption(
    val method: String,
    val stringRes: Int,
    val icon: ImageVector,
)
