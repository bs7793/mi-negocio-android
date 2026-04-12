package superapps.minegocio.ui.productsscreen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import superapps.minegocio.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProductsViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var isCreateSheetOpen by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var createSubmitAttempted by rememberSaveable { mutableStateOf(false) }
    var selectedCategoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedWarehouseId by rememberSaveable { mutableStateOf<Long?>(null) }
    var categoryMenuOpen by rememberSaveable { mutableStateOf(false) }
    var warehouseMenuOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(createSubmitAttempted, uiState.isCreatingProduct, uiState.createErrorMessage) {
        if (createSubmitAttempted && !uiState.isCreatingProduct) {
            if (uiState.createErrorMessage == null) {
                isCreateSheetOpen = false
            }
            createSubmitAttempted = false
        }
    }

    BackHandler(onBack = onNavigateUp)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.products_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_up),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            isCreateSheetOpen = true
                            viewModel.clearCreateError()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.cd_add_product),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    viewModel.setSearchQuery(it)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.products_search_label)) },
                placeholder = { Text(stringResource(R.string.products_search_placeholder)) },
                singleLine = true,
            )

            Button(
                onClick = { viewModel.submitSearch() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.products_search_action))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.categories.firstOrNull { it.id == selectedCategoryId }?.name
                        ?: stringResource(R.string.products_filter_all_categories),
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.products_filter_category_label)) },
                    trailingIcon = {
                        IconButton(onClick = { categoryMenuOpen = !categoryMenuOpen }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                        }
                    },
                )
                DropdownMenu(
                    expanded = categoryMenuOpen,
                    onDismissRequest = { categoryMenuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.products_filter_all_categories)) },
                        onClick = {
                            selectedCategoryId = null
                            categoryMenuOpen = false
                        },
                    )
                    uiState.categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.name) },
                            onClick = {
                                selectedCategoryId = category.id
                                categoryMenuOpen = false
                            },
                        )
                    }
                }

                OutlinedTextField(
                    value = uiState.warehouses.firstOrNull { it.id == selectedWarehouseId }?.name
                        ?: stringResource(R.string.products_filter_all_warehouses),
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.products_filter_warehouse_label)) },
                    trailingIcon = {
                        IconButton(onClick = { warehouseMenuOpen = !warehouseMenuOpen }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                        }
                    },
                )
                DropdownMenu(
                    expanded = warehouseMenuOpen,
                    onDismissRequest = { warehouseMenuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.products_filter_all_warehouses)) },
                        onClick = {
                            selectedWarehouseId = null
                            warehouseMenuOpen = false
                        },
                    )
                    uiState.warehouses.forEach { warehouse ->
                        DropdownMenuItem(
                            text = { Text(warehouse.name) },
                            onClick = {
                                selectedWarehouseId = warehouse.id
                                warehouseMenuOpen = false
                            },
                        )
                    }
                }
            }

            Button(
                onClick = { viewModel.applyFilters(selectedCategoryId, selectedWarehouseId) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.products_filter_action))
            }

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                uiState.errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = uiState.errorMessage.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(
                            onClick = { viewModel.refreshProducts() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.products_retry))
                        }
                    }
                }

                uiState.products.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.products_empty_state),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> {
                    Text(
                        text = stringResource(R.string.products_total_label, uiState.total),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            items = uiState.products,
                            key = { it.productId },
                        ) { product ->
                            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = product.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (!product.categoryName.isNullOrBlank()) {
                                        Text(
                                            text = product.categoryName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.products_variants_count_label, product.variantsCount),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        Text(
                                            text = stringResource(R.string.products_stock_total_label, product.totalStock),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    product.variants.take(3).forEach { variant ->
                                        val options = variant.options.joinToString(" / ") { "${it.type}: ${it.value}" }
                                        Text(
                                            text = if (options.isBlank()) {
                                                "${variant.sku} · ${variant.unitPrice}"
                                            } else {
                                                "${variant.sku} · ${variant.unitPrice} · $options"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (isCreateSheetOpen) {
        CreateProductBottomSheet(
            categories = uiState.categories,
            warehouses = uiState.warehouses,
            optionTypesCatalog = uiState.optionTypesCatalog,
            isSubmitting = uiState.isCreatingProduct,
            errorMessage = uiState.createErrorMessage,
            onDismissRequest = {
                isCreateSheetOpen = false
                createSubmitAttempted = false
            },
            onCreateProduct = { payload ->
                viewModel.createProduct(payload)
                createSubmitAttempted = true
            },
            onClearError = { viewModel.clearCreateError() },
        )
    }
}

