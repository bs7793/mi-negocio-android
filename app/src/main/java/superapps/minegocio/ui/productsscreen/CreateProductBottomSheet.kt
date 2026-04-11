package superapps.minegocio.ui.productsscreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import superapps.minegocio.R
import superapps.minegocio.ui.categoriesscreen.Category
import superapps.minegocio.ui.warehousesscreen.Warehouse

private data class VariantDraft(
    val sku: String = "",
    val unitPrice: String = "",
    val barcode: String = "",
    val optionType: String = "",
    val optionValue: String = "",
    val warehouseQuantities: Map<Long, String> = emptyMap(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProductBottomSheet(
    categories: List<Category>,
    warehouses: List<Warehouse>,
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismissRequest: () -> Unit,
    onCreateProduct: (CreateProductPayload) -> Unit,
    onClearError: () -> Unit,
) {
    var productName by remember { mutableStateOf("") }
    var productDescription by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var isCategoryMenuOpen by remember { mutableStateOf(false) }
    var variants by remember { mutableStateOf(listOf(VariantDraft())) }
    var submitAttempted by remember { mutableStateOf(false) }

    val isProductNameInvalid = submitAttempted && productName.isBlank()
    val hasVariantErrors = submitAttempted && variants.any { it.sku.isBlank() || it.unitPrice.toDoubleOrNull() == null }

    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.products_add_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.products_add_sheet_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = productName,
                onValueChange = {
                    productName = it
                    if (errorMessage != null) onClearError()
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.products_field_name)) },
                placeholder = { Text(stringResource(R.string.products_field_name_placeholder)) },
                singleLine = true,
                isError = isProductNameInvalid,
            )
            if (isProductNameInvalid) {
                Text(
                    text = stringResource(R.string.products_field_name_required),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            OutlinedTextField(
                value = productDescription,
                onValueChange = {
                    productDescription = it
                    if (errorMessage != null) onClearError()
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.products_field_description)) },
                placeholder = { Text(stringResource(R.string.products_field_description_placeholder)) },
                minLines = 2,
                maxLines = 4,
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = categories.firstOrNull { it.id == selectedCategoryId }?.name ?: stringResource(R.string.products_category_none),
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.products_field_category)) },
                    trailingIcon = {
                        IconButton(onClick = { isCategoryMenuOpen = !isCategoryMenuOpen }) {
                            Icon(
                                imageVector = if (isCategoryMenuOpen) Icons.Default.Close else Icons.Default.Add,
                                contentDescription = null,
                            )
                        }
                    },
                )
                DropdownMenu(
                    expanded = isCategoryMenuOpen,
                    onDismissRequest = { isCategoryMenuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.products_category_none)) },
                        onClick = {
                            selectedCategoryId = null
                            isCategoryMenuOpen = false
                        },
                    )
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.name) },
                            onClick = {
                                selectedCategoryId = category.id
                                isCategoryMenuOpen = false
                            },
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.products_variants_section_title),
                style = MaterialTheme.typography.titleMedium,
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(variants) { index, variant ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stringResource(R.string.products_variant_label, index + 1),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            if (variants.size > 1) {
                                IconButton(
                                    onClick = {
                                        variants = variants.filterIndexed { i, _ -> i != index }
                                    },
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null)
                                }
                            }
                        }

                        OutlinedTextField(
                            value = variant.sku,
                            onValueChange = { value ->
                                variants = variants.mapIndexed { i, item ->
                                    if (i == index) item.copy(sku = value) else item
                                }
                                if (errorMessage != null) onClearError()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.products_field_sku)) },
                            placeholder = { Text(stringResource(R.string.products_field_sku_placeholder)) },
                            isError = submitAttempted && variant.sku.isBlank(),
                            singleLine = true,
                        )

                        OutlinedTextField(
                            value = variant.unitPrice,
                            onValueChange = { value ->
                                variants = variants.mapIndexed { i, item ->
                                    if (i == index) item.copy(unitPrice = value) else item
                                }
                                if (errorMessage != null) onClearError()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.products_field_unit_price)) },
                            placeholder = { Text(stringResource(R.string.products_field_unit_price_placeholder)) },
                            singleLine = true,
                            isError = submitAttempted && variant.unitPrice.toDoubleOrNull() == null,
                        )

                        OutlinedTextField(
                            value = variant.barcode,
                            onValueChange = { value ->
                                variants = variants.mapIndexed { i, item ->
                                    if (i == index) item.copy(barcode = value) else item
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.products_field_barcode)) },
                            placeholder = { Text(stringResource(R.string.products_field_barcode_placeholder)) },
                            singleLine = true,
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = variant.optionType,
                                onValueChange = { value ->
                                    variants = variants.mapIndexed { i, item ->
                                        if (i == index) item.copy(optionType = value) else item
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                label = { Text(stringResource(R.string.products_field_option_type)) },
                                placeholder = { Text("Size") },
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = variant.optionValue,
                                onValueChange = { value ->
                                    variants = variants.mapIndexed { i, item ->
                                        if (i == index) item.copy(optionValue = value) else item
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                label = { Text(stringResource(R.string.products_field_option_value)) },
                                placeholder = { Text("M") },
                                singleLine = true,
                            )
                        }

                        Text(
                            text = stringResource(R.string.products_inventory_per_warehouse),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        warehouses.forEach { warehouse ->
                            OutlinedTextField(
                                value = variant.warehouseQuantities[warehouse.id].orEmpty(),
                                onValueChange = { value ->
                                    variants = variants.mapIndexed { i, item ->
                                        if (i == index) {
                                            item.copy(
                                                warehouseQuantities = item.warehouseQuantities + (warehouse.id to value),
                                            )
                                        } else {
                                            item
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(warehouse.name) },
                                placeholder = { Text("0") },
                                singleLine = true,
                            )
                        }
                    }
                }
            }

            Button(
                onClick = { variants = variants + VariantDraft() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSubmitting,
            ) {
                Text(stringResource(R.string.products_action_add_variant))
            }

            if (hasVariantErrors) {
                Text(
                    text = stringResource(R.string.products_variant_validation_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
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
                onClick = {
                    submitAttempted = true
                    val payload = buildPayloadOrNull(
                        name = productName,
                        description = productDescription,
                        categoryId = selectedCategoryId,
                        variants = variants,
                        warehouses = warehouses,
                    ) ?: return@Button
                    onCreateProduct(payload)
                },
                enabled = !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            ) {
                if (isSubmitting) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(stringResource(R.string.products_action_creating))
                    }
                } else {
                    Text(stringResource(R.string.products_action_add))
                }
            }
        }
    }
}

private fun buildPayloadOrNull(
    name: String,
    description: String,
    categoryId: Long?,
    variants: List<VariantDraft>,
    warehouses: List<Warehouse>,
): CreateProductPayload? {
    val trimmedName = name.trim()
    if (trimmedName.isBlank()) return null
    if (variants.isEmpty()) return null

    val variantInputs = variants.mapNotNull { draft ->
        val sku = draft.sku.trim()
        val unitPrice = draft.unitPrice.toDoubleOrNull()
        if (sku.isBlank() || unitPrice == null) return null

        val optionInputs = if (draft.optionType.isNotBlank() && draft.optionValue.isNotBlank()) {
            listOf(
                ProductOptionInput(
                    type = draft.optionType.trim(),
                    value = draft.optionValue.trim(),
                ),
            )
        } else {
            emptyList()
        }

        val inventoryRows = warehouses.map { warehouse ->
            VariantInventoryInput(
                warehouseId = warehouse.id,
                quantity = draft.warehouseQuantities[warehouse.id]?.toDoubleOrNull() ?: 0.0,
            )
        }

        ProductVariantInput(
            sku = sku,
            barcode = draft.barcode.trim().takeUnless { it.isBlank() },
            unitPrice = unitPrice,
            optionValues = optionInputs,
            inventory = inventoryRows,
        )
    }

    if (variantInputs.size != variants.size) return null

    return CreateProductPayload(
        name = trimmedName,
        description = description.trim().takeUnless { it.isBlank() },
        categoryId = categoryId,
        variants = variantInputs,
    )
}

