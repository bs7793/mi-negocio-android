package superapps.minegocio.ui.salesscreen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import superapps.minegocio.R
import superapps.minegocio.ui.warehousesscreen.Warehouse

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesScreen(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SalesViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var warehouseMenuOpen by rememberSaveable { mutableStateOf(false) }
    var showPaymentSheet by rememberSaveable { mutableStateOf(false) }
    var showCustomerSheet by rememberSaveable { mutableStateOf(false) }
    var showReferenceField by rememberSaveable { mutableStateOf(false) }
    val paymentSheetState = androidx.compose.material3.rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )
    val customerSheetState = androidx.compose.material3.rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )
    val coroutineScope = rememberCoroutineScope()

    BackHandler(onBack = onNavigateUp)

    LaunchedEffect(uiState.successMessage, showPaymentSheet) {
        if (showPaymentSheet && !uiState.successMessage.isNullOrBlank()) {
            paymentSheetState.hide()
            showPaymentSheet = false
            showReferenceField = false
        }
    }

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
        bottomBar = {
            SalesChargeBar(
                cartTotal = uiState.cartTotal,
                hasCartItems = uiState.cartItems.isNotEmpty(),
                isSubmitting = uiState.isSubmitting,
                onCharge = {
                    showPaymentSheet = true
                    showReferenceField = false
                },
            )
        },
    ) { innerPadding ->
        CartStepContent(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            searchQuery = uiState.searchQuery,
            onSearchQueryChange = viewModel::setSearchQuery,
            onSearch = viewModel::searchVariants,
            warehouses = uiState.warehouses,
            selectedWarehouseId = uiState.selectedWarehouseId,
            warehouseMenuOpen = warehouseMenuOpen,
            onWarehouseMenuOpenChange = { warehouseMenuOpen = it },
            onSelectWarehouse = viewModel::selectWarehouse,
            errorMessage = uiState.errorMessage,
            successMessage = uiState.successMessage,
            isLoading = uiState.isLoading,
            variants = uiState.variants,
            hasSearched = uiState.hasSearched,
            onClearSearch = viewModel::clearSearchAndReload,
            onAddVariant = viewModel::addVariantToCart,
            cartItems = uiState.cartItems,
            customerNameInput = uiState.customerNameInput,
            notesInput = uiState.notesInput,
            onOpenCustomerSheet = { showCustomerSheet = true },
            onIncrement = viewModel::incrementQuantity,
            onDecrement = viewModel::decrementQuantity,
            onRemove = viewModel::removeFromCart,
            onQuantityChange = viewModel::updateQuantity,
            onUnitPriceChange = viewModel::updateUnitPrice,
        )
        if (showPaymentSheet) {
            ModalBottomSheet(
                onDismissRequest = {
                    showPaymentSheet = false
                    showReferenceField = false
                },
                sheetState = paymentSheetState,
            ) {
                PaymentMethodSheet(
                    selectedMethod = uiState.paymentDraft.method,
                    onSelectMethod = viewModel::updatePaymentMethod,
                    referenceInput = uiState.paymentDraft.reference,
                    onReferenceChange = viewModel::updatePaymentReference,
                    showReferenceField = showReferenceField,
                    onToggleReference = { showReferenceField = !showReferenceField },
                    cartTotal = uiState.cartTotal,
                    isSubmitting = uiState.isSubmitting,
                    errorMessage = uiState.errorMessage,
                    onCharge = viewModel::checkout,
                )
            }
        }
        if (showCustomerSheet) {
            ModalBottomSheet(
                onDismissRequest = { showCustomerSheet = false },
                sheetState = customerSheetState,
            ) {
                CustomerSheetContent(
                    customerName = uiState.customerNameInput,
                    onCustomerNameChange = viewModel::updateCustomerName,
                    notes = uiState.notesInput,
                    onNotesChange = viewModel::updateNotes,
                    onDone = {
                        coroutineScope.launch {
                            customerSheetState.hide()
                            showCustomerSheet = false
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SalesChargeBar(
    cartTotal: Double,
    hasCartItems: Boolean,
    isSubmitting: Boolean,
    onCharge: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Button(
            onClick = onCharge,
            enabled = hasCartItems && !isSubmitting,
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
private fun CartStepContent(
    modifier: Modifier = Modifier,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    warehouses: List<Warehouse>,
    selectedWarehouseId: Long?,
    warehouseMenuOpen: Boolean,
    onWarehouseMenuOpenChange: (Boolean) -> Unit,
    onSelectWarehouse: (Long) -> Unit,
    errorMessage: String?,
    successMessage: String?,
    isLoading: Boolean,
    variants: List<SellableVariant>,
    hasSearched: Boolean,
    onClearSearch: () -> Unit,
    onAddVariant: (SellableVariant) -> Unit,
    cartItems: List<SaleCartItem>,
    customerNameInput: String,
    notesInput: String,
    onOpenCustomerSheet: () -> Unit,
    onIncrement: (Long) -> Unit,
    onDecrement: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    onQuantityChange: (Long, String) -> Unit,
    onUnitPriceChange: (Long, String) -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.sales_search_label)) },
                    placeholder = { Text(stringResource(R.string.sales_search_placeholder)) },
                    singleLine = true,
                )
                Button(onClick = onSearch) {
                    Text(stringResource(R.string.sales_search_action))
                }
            }
        }

        item {
            Box(modifier = Modifier.fillMaxWidth()) {
                val selectedWarehouseName = warehouses
                    .firstOrNull { it.id == selectedWarehouseId }
                    ?.name
                    ?: stringResource(R.string.sales_select_warehouse_placeholder)
                OutlinedTextField(
                    value = selectedWarehouseName,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.sales_warehouse_label)) },
                    trailingIcon = {
                        IconButton(onClick = { onWarehouseMenuOpenChange(true) }) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.sales_select_warehouse_label),
                            )
                        }
                    },
                )
                DropdownMenu(
                    expanded = warehouseMenuOpen,
                    onDismissRequest = { onWarehouseMenuOpenChange(false) },
                ) {
                    warehouses.forEach { warehouse ->
                        DropdownMenuItem(
                            text = { Text(warehouse.name) },
                            onClick = {
                                onWarehouseMenuOpenChange(false)
                                onSelectWarehouse(warehouse.id)
                            },
                        )
                    }
                }
            }
        }

        if (errorMessage != null) {
            item {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (successMessage != null) {
            item {
                Text(
                    text = successMessage,
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

        if (isLoading) {
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
        } else if (variants.isEmpty()) {
            item {
                NoSellableVariantsState(
                    hasSearched = hasSearched,
                    onClearSearch = onClearSearch,
                )
            }
        } else {
            items(
                items = variants,
                key = { "catalog-${it.variantId}" },
            ) { variant ->
                SellableVariantCard(
                    modifier = Modifier.heightIn(max = 180.dp),
                    variant = variant,
                    onAdd = { onAddVariant(variant) },
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
        item {
            CustomerSummaryRow(
                customerName = customerNameInput,
                notes = notesInput,
                onOpenCustomerSheet = onOpenCustomerSheet,
            )
        }

        if (cartItems.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.sales_cart_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(
                items = cartItems,
                key = { "cart-${it.variant.variantId}" },
            ) { item ->
                CartItemCard(
                    item = item,
                    onIncrement = { onIncrement(item.variant.variantId) },
                    onDecrement = { onDecrement(item.variant.variantId) },
                    onRemove = { onRemove(item.variant.variantId) },
                    onQuantityChange = { onQuantityChange(item.variant.variantId, it) },
                    onUnitPriceChange = { onUnitPriceChange(item.variant.variantId, it) },
                )
            }
        }
    }
}

@Composable
private fun CustomerSummaryRow(
    customerName: String,
    notes: String,
    onOpenCustomerSheet: () -> Unit,
) {
    val hasNamedCustomer = customerName.isNotBlank() && !customerName.equals("Public", ignoreCase = true)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AssistChip(
            onClick = onOpenCustomerSheet,
            label = {
                Text(
                    text = if (hasNamedCustomer) {
                        stringResource(R.string.sales_customer_summary_label, customerName)
                    } else {
                        stringResource(R.string.sales_add_customer_action)
                    },
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                )
            },
        )
        if (notes.isNotBlank()) {
            Text(
                text = notes,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CustomerSheetContent(
    customerName: String,
    onCustomerNameChange: (String) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.sales_customer_sheet_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
            value = customerName,
            onValueChange = onCustomerNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.sales_customer_label)) },
            singleLine = true,
        )
        OutlinedTextField(
            value = notes,
            onValueChange = onNotesChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.sales_notes_label)) },
            singleLine = true,
        )
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.sales_save_customer_action))
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            val imageModifier = Modifier
                .size(72.dp)
                .clip(MaterialTheme.shapes.medium)
            if (!variant.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = variant.imageUrl,
                    contentDescription = stringResource(
                        R.string.products_list_image_content_description,
                        variant.productName,
                    ),
                    modifier = imageModifier,
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = imageModifier.background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
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
            }
            IconButton(onClick = onAdd) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.sales_add_to_cart_cd),
                )
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

