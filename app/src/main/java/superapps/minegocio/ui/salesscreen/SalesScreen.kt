package superapps.minegocio.ui.salesscreen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import superapps.minegocio.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesScreen(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SalesViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var warehouseMenuOpen by rememberSaveable { mutableStateOf(false) }

    BackHandler(onBack = onNavigateUp)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sales_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_up),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = viewModel::setSearchQuery,
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.sales_search_label)) },
                        placeholder = { Text(stringResource(R.string.sales_search_placeholder)) },
                        singleLine = true,
                    )
                    Button(onClick = { viewModel.searchVariants() }) {
                        Text(stringResource(R.string.sales_search_action))
                    }
                }
            }

            item {
                Box(modifier = Modifier.fillMaxWidth()) {
                    val selectedWarehouseName = uiState.warehouses
                        .firstOrNull { it.id == uiState.selectedWarehouseId }
                        ?.name
                        ?: stringResource(R.string.sales_select_warehouse_placeholder)
                    OutlinedTextField(
                        value = selectedWarehouseName,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.sales_warehouse_label)) },
                        trailingIcon = {
                            IconButton(onClick = { warehouseMenuOpen = true }) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = stringResource(R.string.sales_select_warehouse_label),
                                )
                            }
                        },
                    )
                    DropdownMenu(
                        expanded = warehouseMenuOpen,
                        onDismissRequest = { warehouseMenuOpen = false },
                    ) {
                        uiState.warehouses.forEach { warehouse ->
                            DropdownMenuItem(
                                text = { Text(warehouse.name) },
                                onClick = {
                                    warehouseMenuOpen = false
                                    viewModel.selectWarehouse(warehouse.id)
                                },
                            )
                        }
                    }
                }
            }

            item {
                DailySummaryCard(summary = uiState.dailySummary)
            }

            if (uiState.errorMessage != null) {
                item {
                    Text(
                        text = uiState.errorMessage.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (uiState.successMessage != null) {
                item {
                    Text(
                        text = uiState.successMessage.orEmpty(),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.sales_products_section_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (uiState.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (uiState.variants.isEmpty()) {
                item {
                    NoSellableVariantsState(
                        hasSearched = uiState.hasSearched,
                        onClearSearch = viewModel::clearSearchAndReload,
                    )
                }
            } else {
                items(
                    items = uiState.variants,
                    key = { it.variantId },
                ) { variant ->
                    SellableVariantCard(
                        modifier = Modifier.heightIn(max = 180.dp),
                        variant = variant,
                        onAdd = { viewModel.addVariantToCart(variant) },
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.sales_cart_section_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (uiState.cartItems.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.sales_cart_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(
                    items = uiState.cartItems,
                    key = { it.variant.variantId },
                ) { item ->
                    CartItemCard(
                        item = item,
                        onIncrement = { viewModel.incrementQuantity(item.variant.variantId) },
                        onDecrement = { viewModel.decrementQuantity(item.variant.variantId) },
                        onRemove = { viewModel.removeFromCart(item.variant.variantId) },
                        onQuantityChange = { viewModel.updateQuantity(item.variant.variantId, it) },
                        onUnitPriceChange = { viewModel.updateUnitPrice(item.variant.variantId, it) },
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = uiState.customerNameInput,
                    onValueChange = viewModel::updateCustomerName,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.sales_customer_label)) },
                    singleLine = true,
                )
            }

            item {
                OutlinedTextField(
                    value = uiState.notesInput,
                    onValueChange = viewModel::updateNotes,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.sales_notes_label)) },
                    singleLine = true,
                )
            }

            item {
                Text(
                    text = stringResource(R.string.sales_payment_method_label),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            item {
                PaymentMethodSelector(
                    selected = uiState.paymentDraft.method,
                    onSelect = viewModel::updatePaymentMethod,
                )
            }
            item {
                OutlinedTextField(
                    value = uiState.paymentDraft.amountInput,
                    onValueChange = viewModel::updatePaymentAmount,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.sales_payment_amount_label)) },
                    singleLine = true,
                )
            }
            item {
                OutlinedTextField(
                    value = uiState.paymentDraft.reference,
                    onValueChange = viewModel::updatePaymentReference,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.sales_payment_reference_label)) },
                    singleLine = true,
                )
            }

            item {
                Text(
                    text = stringResource(R.string.sales_total_label, uiState.cartTotal),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            item {
                Button(
                    onClick = { viewModel.checkout() },
                    enabled = !uiState.isSubmitting && uiState.cartItems.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (uiState.isSubmitting) {
                            stringResource(R.string.sales_checkout_processing)
                        } else {
                            stringResource(R.string.sales_checkout_action)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DailySummaryCard(summary: SalesDailySummary) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.sales_daily_summary_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(
                    R.string.sales_daily_summary_totals,
                    summary.salesCount,
                    summary.unitsSold,
                    summary.grossTotal,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(
                    R.string.sales_daily_summary_breakdown,
                    summary.cashTotal,
                    summary.cardTotal,
                    summary.transferTotal,
                    summary.otherTotal,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SellableVariantCard(
    modifier: Modifier = Modifier,
    variant: SellableVariant,
    onAdd: () -> Unit,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = variant.productName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.sales_variant_details,
                    variant.sku,
                    variant.stockTotal,
                    variant.unitPrice,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            if (!variant.barcode.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.sales_variant_barcode, variant.barcode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (variant.options.isNotEmpty()) {
                val optionsText = variant.options.joinToString(" / ") { "${it.type}: ${it.value}" }
                Text(
                    text = optionsText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onAdd) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.sales_add_to_cart_cd),
                    )
                }
            }
        }
    }
}

@Composable
private fun NoSellableVariantsState(
    hasSearched: Boolean,
    onClearSearch: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.sales_search_empty_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (hasSearched) {
                Button(
                    onClick = onClearSearch,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.sales_clear_search_action))
                }
            }
        }
    }
}

@Composable
private fun CartItemCard(
    item: SaleCartItem,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onRemove: () -> Unit,
    onQuantityChange: (String) -> Unit,
    onUnitPriceChange: (String) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = item.variant.productName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.sales_variant_sku_label, item.variant.sku),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDecrement) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = stringResource(R.string.sales_decrement_qty_cd),
                    )
                }
                OutlinedTextField(
                    value = item.quantityInput,
                    onValueChange = onQuantityChange,
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.sales_qty_label)) },
                    singleLine = true,
                )
                IconButton(onClick = onIncrement) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.sales_increment_qty_cd),
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.sales_remove_item_cd),
                    )
                }
            }
            OutlinedTextField(
                value = item.unitPriceInput,
                onValueChange = onUnitPriceChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.sales_applied_price_label)) },
                singleLine = true,
            )
            Text(
                text = stringResource(R.string.sales_line_total_label, item.lineTotal),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PaymentMethodSelector(
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PaymentMethodChip(
            method = "cash",
            label = stringResource(R.string.sales_payment_cash),
            selected = selected == "cash",
            onSelect = onSelect,
        )
        PaymentMethodChip(
            method = "card",
            label = stringResource(R.string.sales_payment_card),
            selected = selected == "card",
            onSelect = onSelect,
        )
        PaymentMethodChip(
            method = "transfer",
            label = stringResource(R.string.sales_payment_transfer),
            selected = selected == "transfer",
            onSelect = onSelect,
        )
        PaymentMethodChip(
            method = "other",
            label = stringResource(R.string.sales_payment_other),
            selected = selected == "other",
            onSelect = onSelect,
        )
    }
}

@Composable
private fun PaymentMethodChip(
    method: String,
    label: String,
    selected: Boolean,
    onSelect: (String) -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = { onSelect(method) },
        label = { Text(label) },
    )
}
