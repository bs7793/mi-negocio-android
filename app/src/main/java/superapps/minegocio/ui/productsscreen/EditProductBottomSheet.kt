package superapps.minegocio.ui.productsscreen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.LaunchedEffect
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
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import superapps.minegocio.R
import superapps.minegocio.ui.categoriesscreen.Category
import java.io.ByteArrayOutputStream
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProductBottomSheet(
    product: Product,
    categories: List<Category>,
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismissRequest: () -> Unit,
    onUpdateProduct: (
        payload: UpdateProductBasicPayload,
        imageUpload: ProductImageUpload?,
        previousImageUrl: String?,
    ) -> Unit,
    onClearError: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var productName by remember(product.productId) { mutableStateOf(product.name) }
    var productDescription by remember(product.productId) { mutableStateOf(product.description.orEmpty()) }
    var selectedCategoryId by remember(product.productId) { mutableStateOf(product.categoryId) }
    var isCategoryMenuOpen by remember(product.productId) { mutableStateOf(false) }
    var currentImageUrl by remember(product.productId) { mutableStateOf(product.imageUrl) }
    var selectedImageUri by remember(product.productId) { mutableStateOf<Uri?>(null) }
    var selectedImageUpload by remember(product.productId) { mutableStateOf<ProductImageUpload?>(null) }
    var cameraImageUri by remember(product.productId) { mutableStateOf<Uri?>(null) }
    var isPreparingImage by remember(product.productId) { mutableStateOf(false) }
    var imageErrorMessage by remember(product.productId) { mutableStateOf<String?>(null) }
    var submitAttempted by remember(product.productId) { mutableStateOf(false) }

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
                currentImageUrl = null
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
                currentImageUrl = null
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

    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.products_edit_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.products_edit_sheet_subtitle),
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
                when {
                    selectedImageUri != null -> {
                        AsyncImage(
                            model = selectedImageUri,
                            contentDescription = stringResource(R.string.products_image_preview_description),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                        )
                    }

                    !currentImageUrl.isNullOrBlank() -> {
                        AsyncImage(
                            model = currentImageUrl,
                            contentDescription = stringResource(R.string.products_image_preview_description),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                        )
                    }
                }
                if (selectedImageUri != null || !currentImageUrl.isNullOrBlank()) {
                    Button(
                        onClick = {
                            selectedImageUri = null
                            selectedImageUpload = null
                            currentImageUrl = null
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
                    val trimmedName = productName.trim()
                    if (trimmedName.isBlank()) return@Button
                    val payload = UpdateProductBasicPayload(
                        productId = product.productId,
                        name = trimmedName,
                        description = productDescription.trim().takeUnless { it.isBlank() },
                        categoryId = selectedCategoryId,
                        imageUrl = if (selectedImageUpload != null) null else currentImageUrl,
                    )
                    onUpdateProduct(
                        payload,
                        selectedImageUpload,
                        product.imageUrl,
                    )
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
                        Text(stringResource(R.string.products_action_saving))
                    }
                } else {
                    Text(stringResource(R.string.products_action_save))
                }
            }
        }
    }
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

private const val MAX_UPLOAD_IMAGE_BYTES = 2_000_000
private const val MAX_IMAGE_DIMENSION = 1600
private const val JPEG_COMPRESSION_QUALITY = 82
private val SUPPORTED_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")
