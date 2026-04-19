package superapps.minegocio.ui.productsscreen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import superapps.minegocio.BuildConfig
import superapps.minegocio.R
import superapps.minegocio.ui.categoriesscreen.Category
import superapps.minegocio.ui.warehousesscreen.Warehouse
import java.io.ByteArrayOutputStream
import java.io.File

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
    onCreateProduct: (CreateProductPayload, ProductImageUpload?) -> Unit,
    onClearError: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var productName by remember { mutableStateOf("") }
    var productDescription by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var isCategoryMenuOpen by remember { mutableStateOf(false) }
    var variants by remember { mutableStateOf(listOf(VariantDraft())) }
    var submitAttempted by remember { mutableStateOf(false) }
    var typeMenuIndexOpen by remember { mutableStateOf<Int?>(null) }
    var valueMenuIndexOpen by remember { mutableStateOf<Int?>(null) }
    var localOptionTypes by remember(optionTypesCatalog) { mutableStateOf(optionTypesCatalog) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedImageUpload by remember { mutableStateOf<ProductImageUpload?>(null) }
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    var isPreparingImage by remember { mutableStateOf(false) }
    var imageErrorMessage by remember { mutableStateOf<String?>(null) }

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        imageErrorMessage = null
        scope.launch {
            isPreparingImage = true
            val imageUpload = withContext(Dispatchers.IO) { createProductImageUpload(context, uri) }
            if (imageUpload == null) {
                selectedImageUpload = null
                imageErrorMessage = context.getString(R.string.products_image_error_process)
            } else {
                selectedImageUri = uri
                selectedImageUpload = imageUpload
                if (errorMessage != null) onClearError()
            }
            isPreparingImage = false
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        if (!success) {
            cameraImageUri = null
            return@rememberLauncherForActivityResult
        }
        val capturedUri = cameraImageUri ?: return@rememberLauncherForActivityResult
        imageErrorMessage = null
        scope.launch {
            isPreparingImage = true
            val imageUpload = withContext(Dispatchers.IO) { createProductImageUpload(context, capturedUri) }
            if (imageUpload == null) {
                selectedImageUpload = null
                imageErrorMessage = context.getString(R.string.products_image_error_process)
            } else {
                selectedImageUri = capturedUri
                selectedImageUpload = imageUpload
                if (errorMessage != null) onClearError()
            }
            isPreparingImage = false
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            imageErrorMessage = context.getString(R.string.products_image_error_permission)
            return@rememberLauncherForActivityResult
        }
        val uri = createTempImageUri(context)
        if (uri == null) {
            imageErrorMessage = context.getString(R.string.products_image_error_create_uri)
            return@rememberLauncherForActivityResult
        }
        cameraImageUri = uri
        cameraLauncher.launch(uri)
    }

    LaunchedEffect(errorMessage) {
        if (errorMessage == null) return@LaunchedEffect
        imageErrorMessage = null
    }

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
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
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

            if (BuildConfig.DEBUG) {
                OutlinedButton(
                    onClick = {
                        submitAttempted = false
                        typeMenuIndexOpen = null
                        valueMenuIndexOpen = null
                        isCategoryMenuOpen = false
                        localOptionTypes = optionTypesCatalog
                        productName = "Debug product ${System.currentTimeMillis()}"
                        productDescription =
                            "Dummy description for development and QA. Safe to delete."
                        selectedCategoryId = categories.firstOrNull()?.id
                        variants = listOf(
                            buildDummyVariantDraft(
                                warehouses = warehouses,
                                optionTypesCatalog = optionTypesCatalog,
                            ),
                        )
                        if (errorMessage != null) onClearError()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting && !isPreparingImage,
                ) {
                    Text(stringResource(R.string.products_debug_fill_dummy_data))
                }
            }

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
                            if (errorMessage != null) onClearError()
                        },
                    )
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.name) },
                            onClick = {
                                selectedCategoryId = category.id
                                isCategoryMenuOpen = false
                                if (errorMessage != null) onClearError()
                            },
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.products_image_section_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.products_image_section_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            imageErrorMessage = null
                            if (hasCameraPermission(context)) {
                                val uri = createTempImageUri(context)
                                if (uri == null) {
                                    imageErrorMessage =
                                        context.getString(R.string.products_image_error_create_uri)
                                } else {
                                    cameraImageUri = uri
                                    cameraLauncher.launch(uri)
                                }
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isSubmitting && !isPreparingImage,
                    ) {
                        Text(stringResource(R.string.products_image_action_take_photo))
                    }
                    Button(
                        onClick = {
                            imageErrorMessage = null
                            galleryPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isSubmitting && !isPreparingImage,
                    ) {
                        Text(stringResource(R.string.products_image_action_choose_gallery))
                    }
                }
                if (selectedImageUri != null) {
                    AsyncImage(
                        model = selectedImageUri,
                        contentDescription = stringResource(R.string.products_image_preview_description),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                    )
                    Button(
                        onClick = {
                            selectedImageUri = null
                            selectedImageUpload = null
                            if (errorMessage != null) onClearError()
                        },
                        enabled = !isSubmitting && !isPreparingImage,
                    ) {
                        Text(stringResource(R.string.products_image_action_remove))
                    }
                }
                if (isPreparingImage) {
                    Text(
                        text = stringResource(R.string.products_image_processing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!imageErrorMessage.isNullOrBlank()) {
                    Text(
                        text = imageErrorMessage.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Text(
                text = stringResource(R.string.products_variants_section_title),
                style = MaterialTheme.typography.titleMedium,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                variants.forEachIndexed { index, variant ->
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
                                        if (errorMessage != null) onClearError()
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
                                if (errorMessage != null) onClearError()
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
                                        if (errorMessage != null) onClearError()
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
                                            if (errorMessage != null) onClearError()
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
                                        if (errorMessage != null) onClearError()
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
                                            if (errorMessage != null) onClearError()
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
                                    if (errorMessage != null) onClearError()
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
                    onCreateProduct(payload, selectedImageUpload)
                },
                enabled = !isSubmitting && !isPreparingImage,
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

private fun buildDummyVariantDraft(
    warehouses: List<Warehouse>,
    optionTypesCatalog: List<ProductOptionTypeCatalog>,
): VariantDraft {
    val warehouseQuantities = warehouses.associate { it.id to "10" }
    val firstTypeWithValue = optionTypesCatalog.firstOrNull { it.values.isNotEmpty() }
    val selectedType = firstTypeWithValue?.name
    val selectedValue = firstTypeWithValue?.values?.firstOrNull()?.value
    return VariantDraft(
        sku = "DUMMY-${System.currentTimeMillis()}",
        costPrice = "8.50",
        unitPrice = "19.99",
        barcode = "5901234123457",
        selectedOptionType = selectedType,
        selectedOptionValue = selectedValue,
        isCreatingNewType = false,
        isCreatingNewValue = false,
        newOptionTypeText = "",
        newOptionValueText = "",
        warehouseQuantities = warehouseQuantities,
    )
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

private fun hasCameraPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA,
    ) == PackageManager.PERMISSION_GRANTED
}

private fun createTempImageUri(context: Context): Uri? {
    return try {
        val imageDir = File(context.cacheDir, "product_images").apply { mkdirs() }
        val imageFile = File.createTempFile("product_", ".jpg", imageDir)
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile,
        )
    } catch (_: Exception) {
        null
    }
}

private fun createProductImageUpload(
    context: Context,
    uri: Uri,
): ProductImageUpload? {
    val resolver = context.contentResolver
    val rawBytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    if (rawBytes.isEmpty()) return null

    val mimeType = resolver.getType(uri).orEmpty()
    val isMimeTypeSupported = mimeType in SUPPORTED_MIME_TYPES
    val shouldTranscode = rawBytes.size > MAX_UPLOAD_IMAGE_BYTES || !isMimeTypeSupported
    val normalizedBytes = if (shouldTranscode) compressAsJpeg(rawBytes) ?: return null else rawBytes
    val normalizedMimeType = if (shouldTranscode) "image/jpeg" else normalizeMimeType(mimeType)
    val normalizedExtension = if (shouldTranscode) "jpg" else mimeTypeToExtension(mimeType)

    return ProductImageUpload(
        bytes = normalizedBytes,
        mimeType = normalizedMimeType,
        fileExtension = normalizedExtension,
    )
}

private fun compressAsJpeg(rawBytes: ByteArray): ByteArray? {
    val decodeBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeBounds)
    var inSampleSize = 1
    while (
        decodeBounds.outWidth / inSampleSize > MAX_IMAGE_DIMENSION ||
        decodeBounds.outHeight / inSampleSize > MAX_IMAGE_DIMENSION
    ) {
        inSampleSize *= 2
    }
    val bitmap = BitmapFactory.decodeByteArray(
        rawBytes,
        0,
        rawBytes.size,
        BitmapFactory.Options().apply { this.inSampleSize = inSampleSize.coerceAtLeast(1) },
    ) ?: return null
    val output = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_COMPRESSION_QUALITY, output)
    bitmap.recycle()
    return output.toByteArray()
}

private fun normalizeMimeType(rawMimeType: String): String {
    return when {
        rawMimeType.startsWith("image/") -> rawMimeType
        else -> "image/jpeg"
    }
}

private fun mimeTypeToExtension(rawMimeType: String): String {
    return when (rawMimeType.lowercase()) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "jpg"
    }
}

/** True when non-blank after trim but not a valid non-negative number. */
private fun isCostPriceDraftInvalid(costPriceDraft: String): Boolean {
    val trimmed = costPriceDraft.trim()
    if (trimmed.isEmpty()) return false
    val value = trimmed.toDoubleOrNull() ?: return true
    return value < 0
}

private const val MAX_UPLOAD_IMAGE_BYTES = 2_000_000
private const val MAX_IMAGE_DIMENSION = 1600
private const val JPEG_COMPRESSION_QUALITY = 82
private val SUPPORTED_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")

