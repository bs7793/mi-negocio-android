package superapps.minegocio.ui.warehousesscreen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import superapps.minegocio.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarehousesScreen(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WarehousesViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var isCreateSheetOpen by rememberSaveable { mutableStateOf(false) }
    var warehouseName by rememberSaveable { mutableStateOf("") }
    var warehouseLocation by rememberSaveable { mutableStateOf("") }
    var warehouseAisle by rememberSaveable { mutableStateOf("") }
    var warehouseShelf by rememberSaveable { mutableStateOf("") }
    var warehouseLevel by rememberSaveable { mutableStateOf("") }
    var warehousePosition by rememberSaveable { mutableStateOf("") }
    var isNameTouched by rememberSaveable { mutableStateOf(false) }
    var submitAttempted by rememberSaveable { mutableStateOf(false) }
    var editingWarehouseId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editSubmitAttempted by remember { mutableStateOf(false) }
    val isNameInvalid = isNameTouched && warehouseName.isBlank()
    val editingWarehouse = editingWarehouseId?.let { id ->
        uiState.warehouses.find { it.id == id }
    }
    val editWarehouseCd = stringResource(R.string.cd_edit_warehouse)
    val createScroll = rememberScrollState()

    BackHandler(onBack = onNavigateUp)

    LaunchedEffect(editingWarehouseId, uiState.warehouses) {
        val id = editingWarehouseId ?: return@LaunchedEffect
        if (uiState.warehouses.none { it.id == id }) {
            editingWarehouseId = null
            viewModel.clearUpdateError()
        }
    }

    LaunchedEffect(submitAttempted, uiState.isCreatingWarehouse, uiState.createErrorMessage) {
        if (submitAttempted && !uiState.isCreatingWarehouse) {
            if (uiState.createErrorMessage == null) {
                isCreateSheetOpen = false
                warehouseName = ""
                warehouseLocation = ""
                warehouseAisle = ""
                warehouseShelf = ""
                warehouseLevel = ""
                warehousePosition = ""
                isNameTouched = false
            }
            submitAttempted = false
        }
    }

    LaunchedEffect(editSubmitAttempted, uiState.isUpdatingWarehouse, uiState.updateErrorMessage) {
        if (editSubmitAttempted && !uiState.isUpdatingWarehouse) {
            if (uiState.updateErrorMessage == null) {
                editingWarehouseId = null
            }
            editSubmitAttempted = false
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.warehouses_screen_title))
                },
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
                            contentDescription = stringResource(R.string.cd_add_warehouse),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
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
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = uiState.errorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            items = uiState.warehouses,
                            key = { it.id },
                        ) { warehouse ->
                            ElevatedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics {
                                        contentDescription = editWarehouseCd
                                    }
                                    .clickable {
                                        editingWarehouseId = warehouse.id
                                        viewModel.clearUpdateError()
                                    },
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = warehouse.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val loc = warehouse.location
                                    if (!loc.isNullOrBlank()) {
                                        Text(
                                            text = loc,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    val slotParts = listOfNotNull(
                                        warehouse.aisle?.takeIf { it.isNotBlank() },
                                        warehouse.shelf?.takeIf { it.isNotBlank() },
                                        warehouse.level?.takeIf { it.isNotBlank() },
                                        warehouse.position?.takeIf { it.isNotBlank() },
                                    )
                                    if (slotParts.isNotEmpty()) {
                                        Text(
                                            text = slotParts.joinToString(" · "),
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

        if (isCreateSheetOpen) {
            ModalBottomSheet(
                onDismissRequest = {
                    isCreateSheetOpen = false
                    submitAttempted = false
                    warehouseName = ""
                    warehouseLocation = ""
                    warehouseAisle = ""
                    warehouseShelf = ""
                    warehouseLevel = ""
                    warehousePosition = ""
                    isNameTouched = false
                    viewModel.clearCreateError()
                },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(createScroll)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.warehouses_add_sheet_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = stringResource(R.string.warehouses_add_sheet_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    OutlinedTextField(
                        value = warehouseName,
                        onValueChange = {
                            warehouseName = it
                            isNameTouched = true
                            if (uiState.createErrorMessage != null) {
                                viewModel.clearCreateError()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.warehouses_field_name)) },
                        placeholder = { Text(stringResource(R.string.warehouses_field_name_placeholder)) },
                        singleLine = true,
                        isError = isNameInvalid,
                    )
                    if (isNameInvalid) {
                        Text(
                            text = stringResource(R.string.warehouses_field_name_required),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    OutlinedTextField(
                        value = warehouseLocation,
                        onValueChange = { warehouseLocation = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.warehouses_field_location)) },
                        placeholder = { Text(stringResource(R.string.warehouses_field_location_placeholder)) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = warehouseAisle,
                        onValueChange = { warehouseAisle = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.warehouses_field_aisle)) },
                        placeholder = { Text(stringResource(R.string.warehouses_field_aisle_placeholder)) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = warehouseShelf,
                        onValueChange = { warehouseShelf = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.warehouses_field_shelf)) },
                        placeholder = { Text(stringResource(R.string.warehouses_field_shelf_placeholder)) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = warehouseLevel,
                        onValueChange = { warehouseLevel = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.warehouses_field_level)) },
                        placeholder = { Text(stringResource(R.string.warehouses_field_level_placeholder)) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = warehousePosition,
                        onValueChange = { warehousePosition = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.warehouses_field_position)) },
                        placeholder = { Text(stringResource(R.string.warehouses_field_position_placeholder)) },
                        singleLine = true,
                    )

                    if (!uiState.createErrorMessage.isNullOrBlank()) {
                        Text(
                            text = uiState.createErrorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    Button(
                        onClick = {
                            submitAttempted = true
                            isNameTouched = true
                            viewModel.createWarehouse(
                                name = warehouseName,
                                location = warehouseLocation.trim().takeUnless { it.isEmpty() },
                                aisle = warehouseAisle.trim().takeUnless { it.isEmpty() },
                                shelf = warehouseShelf.trim().takeUnless { it.isEmpty() },
                                level = warehouseLevel.trim().takeUnless { it.isEmpty() },
                                position = warehousePosition.trim().takeUnless { it.isEmpty() },
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        enabled = !uiState.isCreatingWarehouse && warehouseName.isNotBlank(),
                    ) {
                        if (uiState.isCreatingWarehouse) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Text(
                                    text = stringResource(R.string.warehouses_action_creating),
                                )
                            }
                        } else {
                            Text(text = stringResource(R.string.warehouses_action_add))
                        }
                    }
                }
            }
        }

        editingWarehouse?.let { warehouse ->
            WarehouseEditBottomSheet(
                warehouse = warehouse,
                onDismissRequest = {
                    editingWarehouseId = null
                    editSubmitAttempted = false
                    viewModel.clearUpdateError()
                },
                onSave = { name, location, aisle, shelf, level, position ->
                    editSubmitAttempted = true
                    viewModel.updateWarehouse(
                        id = warehouse.id,
                        name = name,
                        location = location,
                        aisle = aisle,
                        shelf = shelf,
                        level = level,
                        position = position,
                    )
                },
                isSaving = uiState.isUpdatingWarehouse,
                errorMessage = uiState.updateErrorMessage,
                onClearError = { viewModel.clearUpdateError() },
            )
        }
    }
}
