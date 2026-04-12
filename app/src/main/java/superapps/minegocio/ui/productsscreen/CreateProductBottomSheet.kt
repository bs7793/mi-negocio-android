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
    val costPrice: String = "",
    val unitPrice: String = "",
    val barcode: String = "",
    val selectedOptionType: String? = null,
    val selectedOptionValue: String? = null,
    val isCreatingNewType: Boolean = false,
    val isCreatingNewValue: Boolean = false,
    val newOptionTypeText: String = "",
    val newOptionValueText: String = "",
    val warehouseQuantities: Map<Long, String> = emptyMap(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProductBottomSheet(
    categories: List<Category>,
    warehouses: List<Warehouse>,
    optionTypesCatalog: List<ProductOptionTypeCatalog>,
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
    var typeMenuIndexOpen by remember { mutableStateOf<Int?>(null) }
    var valueMenuIndexOpen by remember { mutableStateOf<Int?>(null) }
    var localOptionTypes by remember(optionTypesCatalog) { mutableStateOf(optionTypesCatalog) }

    val isProductNameInvalid = submitAttempted && productName.isBlank()
    val hasVariantErrors = submitAttempted && variants.any { variant ->
        val skuInvalid = variant.sku.isBlank()
        val priceInvalid = variant.unitPrice.toDoubleOrNull() == null
        val costInvalid = isCostPriceDraftInvalid(variant.costPrice)
        val hasInlineTypeError = variant.isCreatingNewType && variant.newOptionTypeText.isBlank()
        val hasInlineValueError = variant.isCreatingNewValue && variant.newOptionValueText.isBlank()
        val hasHalfOptionSelected =
            (variant.selectedOptionType.isNullOrBlank()) xor (variant.selectedOptionValue.isNullOrBlank())
        skuInvalid || priceInvalid || costInvalid || hasInlineTypeError || hasInlineValueError || hasHalfOptionSelected
    }

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
                    value = categories.firstOrNull { it.id == selectedCategoryId }?.name
                        ?: stringResource(R.string.products_category_none),
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
                    .height(280.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(variants) { index, variant ->
                    val selectedType = localOptionTypes.firstOrNull { it.name == variant.selectedOptionType }
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
                            value = variant.costPrice,
                            onValueChange = { value ->
                                variants = variants.mapIndexed { i, item ->
                                    if (i == index) item.copy(costPrice = value) else item
                                }
                                if (errorMessage != null) onClearError()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.products_field_cost_price)) },
                            placeholder = { Text(stringResource(R.string.products_field_cost_price_placeholder)) },
                            singleLine = true,
                            isError = submitAttempted && isCostPriceDraftInvalid(variant.costPrice),
                        )
                        if (submitAttempted && isCostPriceDraftInvalid(variant.costPrice)) {
                            Text(
                                text = stringResource(R.string.products_field_cost_price_invalid),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }

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
                                value = variant.selectedOptionType
                                    ?: stringResource(R.string.products_option_type_none),
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier.weight(1f),
                                label = { Text(stringResource(R.string.products_field_option_type_selector)) },
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = { typeMenuIndexOpen = index }) {
                                        Icon(Icons.Default.Add, contentDescription = null)
                                    }
                                },
                            )
                            DropdownMenu(
                                expanded = typeMenuIndexOpen == index,
                                onDismissRequest = { typeMenuIndexOpen = null },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.products_option_type_none)) },
                                    onClick = {
                                        variants = variants.mapIndexed { i, item ->
                                            if (i == index) {
                                                item.copy(
                                                    selectedOptionType = null,
                                                    selectedOptionValue = null,
                                                )
                                            } else {
                                                item
                                            }
                                        }
                                        typeMenuIndexOpen = null
                                    },
                                )
                                localOptionTypes.forEach { optionType ->
                                    DropdownMenuItem(
                                        text = { Text(optionType.name) },
                                        onClick = {
                                            variants = variants.mapIndexed { i, item ->
                                                if (i == index) {
                                                    item.copy(
                                                        selectedOptionType = optionType.name,
                                                        selectedOptionValue = null,
                                                        isCreatingNewType = false,
                                                        newOptionTypeText = "",
                                                    )
                                                } else {
                                                    item
                                                }
                                            }
                                            typeMenuIndexOpen = null
                                        },
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = variant.selectedOptionValue
                                    ?: stringResource(R.string.products_option_value_none),
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier.weight(1f),
                                label = { Text(stringResource(R.string.products_field_option_value_selector)) },
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = { valueMenuIndexOpen = index }) {
                                        Icon(Icons.Default.Add, contentDescription = null)
                                    }
                                },
                            )
                            DropdownMenu(
                                expanded = valueMenuIndexOpen == index,
                                onDismissRequest = { valueMenuIndexOpen = null },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.products_option_value_none)) },
                                    onClick = {
                                        variants = variants.mapIndexed { i, item ->
                                            if (i == index) item.copy(selectedOptionValue = null) else item
                                        }
                                        valueMenuIndexOpen = null
                                    },
                                )
                                selectedType?.values.orEmpty().forEach { optionValue ->
                                    DropdownMenuItem(
                                        text = { Text(optionValue.value) },
                                        onClick = {
                                            variants = variants.mapIndexed { i, item ->
                                                if (i == index) item.copy(selectedOptionValue = optionValue.value) else item
                                            }
                                            valueMenuIndexOpen = null
                                        },
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    variants = variants.mapIndexed { i, item ->
                                        if (i == index) item.copy(isCreatingNewType = !item.isCreatingNewType) else item
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.products_action_new_option_type))
                            }
                            Button(
                                onClick = {
                                    variants = variants.mapIndexed { i, item ->
                                        if (i == index) item.copy(isCreatingNewValue = !item.isCreatingNewValue) else item
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = variant.selectedOptionType != null || variant.isCreatingNewType,
                            ) {
                                Text(stringResource(R.string.products_action_new_option_value))
                            }
                        }

                        if (variant.isCreatingNewType) {
                            OutlinedTextField(
                                value = variant.newOptionTypeText,
                                onValueChange = { value ->
                                    val normalized = value.trim()
                                    variants = variants.mapIndexed { i, item ->
                                        if (i == index) {
                                            item.copy(
                                                newOptionTypeText = value,
                                                selectedOptionType = normalized.takeUnless { it.isBlank() },
                                                selectedOptionValue = null,
                                            )
                                        } else {
                                            item
                                        }
                                    }
                                    if (normalized.isNotBlank() &&
                                        localOptionTypes.none { it.name.equals(normalized, ignoreCase = true) }
                                    ) {
                                        localOptionTypes = localOptionTypes + ProductOptionTypeCatalog(
                                            id = -1L * (localOptionTypes.size + 1),
                                            name = normalized,
                                            values = emptyList(),
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.products_field_new_option_type)) },
                                singleLine = true,
                                isError = submitAttempted && variant.newOptionTypeText.isBlank(),
                            )
                        }

                        if (variant.isCreatingNewValue) {
                            OutlinedTextField(
                                value = variant.newOptionValueText,
                                onValueChange = { value ->
                                    val normalized = value.trim()
                                    variants = variants.mapIndexed { i, item ->
                                        if (i == index) {
                                            item.copy(
                                                newOptionValueText = value,
                                                selectedOptionValue = normalized.takeUnless { it.isBlank() },
                                            )
                                        } else {
                                            item
                                        }
                                    }
                                    val typeName = variants.getOrNull(index)?.selectedOptionType?.trim().orEmpty()
                                    if (normalized.isNotBlank() && typeName.isNotBlank()) {
                                        localOptionTypes = localOptionTypes.map { type ->
                                            if (!type.name.equals(typeName, ignoreCase = true)) return@map type
                                            val exists = type.values.any { it.value.equals(normalized, ignoreCase = true) }
                                            if (exists) {
                                                type
                                            } else {
                                                type.copy(
                                                    values = type.values + ProductOptionValueCatalog(
                                                        id = -1L * (type.values.size + 1),
                                                        value = normalized,
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.products_field_new_option_value)) },
                                singleLine = true,
                                isError = submitAttempted && variant.newOptionValueText.isBlank(),
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
        if (isCostPriceDraftInvalid(draft.costPrice)) return null

        val costTrimmed = draft.costPrice.trim()
        val costPrice = costTrimmed.toDoubleOrNull()

        val selectedType = draft.selectedOptionType?.trim().orEmpty()
        val selectedValue = draft.selectedOptionValue?.trim().orEmpty()
        if ((selectedType.isBlank()) xor (selectedValue.isBlank())) return null

        val optionInputs = if (selectedType.isNotBlank() && selectedValue.isNotBlank()) {
            listOf(
                ProductOptionInput(
                    type = selectedType,
                    value = selectedValue,
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
            costPrice = costPrice,
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

/** True when non-blank after trim but not a valid non-negative number. */
private fun isCostPriceDraftInvalid(costPriceDraft: String): Boolean {
    val trimmed = costPriceDraft.trim()
    if (trimmed.isEmpty()) return false
    val value = trimmed.toDoubleOrNull() ?: return true
    return value < 0
}

