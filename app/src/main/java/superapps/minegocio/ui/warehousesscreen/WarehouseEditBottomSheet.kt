package superapps.minegocio.ui.warehousesscreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import superapps.minegocio.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarehouseEditBottomSheet(
    warehouse: Warehouse,
    onDismissRequest: () -> Unit,
    onSave: (
        name: String,
        location: String?,
        aisle: String?,
        shelf: String?,
        level: String?,
        position: String?,
    ) -> Unit,
    isSaving: Boolean,
    errorMessage: String?,
    onClearError: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var aisle by remember { mutableStateOf("") }
    var shelf by remember { mutableStateOf("") }
    var level by remember { mutableStateOf("") }
    var position by remember { mutableStateOf("") }
    var isNameTouched by remember { mutableStateOf(false) }

    LaunchedEffect(warehouse.id) {
        name = warehouse.name
        location = warehouse.location.orEmpty()
        aisle = warehouse.aisle.orEmpty()
        shelf = warehouse.shelf.orEmpty()
        level = warehouse.level.orEmpty()
        position = warehouse.position.orEmpty()
        isNameTouched = false
    }

    val isNameInvalid = isNameTouched && name.isBlank()
    val scroll = rememberScrollState()

    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.warehouses_edit_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.warehouses_edit_sheet_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    isNameTouched = true
                    if (errorMessage != null) {
                        onClearError()
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
                value = location,
                onValueChange = { location = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.warehouses_field_location)) },
                placeholder = { Text(stringResource(R.string.warehouses_field_location_placeholder)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = aisle,
                onValueChange = { aisle = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.warehouses_field_aisle)) },
                placeholder = { Text(stringResource(R.string.warehouses_field_aisle_placeholder)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = shelf,
                onValueChange = { shelf = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.warehouses_field_shelf)) },
                placeholder = { Text(stringResource(R.string.warehouses_field_shelf_placeholder)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = level,
                onValueChange = { level = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.warehouses_field_level)) },
                placeholder = { Text(stringResource(R.string.warehouses_field_level_placeholder)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = position,
                onValueChange = { position = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.warehouses_field_position)) },
                placeholder = { Text(stringResource(R.string.warehouses_field_position_placeholder)) },
                singleLine = true,
            )

            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Button(
                onClick = {
                    isNameTouched = true
                    if (name.isNotBlank()) {
                        onSave(
                            name,
                            location.trim().takeUnless { it.isEmpty() },
                            aisle.trim().takeUnless { it.isEmpty() },
                            shelf.trim().takeUnless { it.isEmpty() },
                            level.trim().takeUnless { it.isEmpty() },
                            position.trim().takeUnless { it.isEmpty() },
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                enabled = !isSaving && name.isNotBlank(),
            ) {
                if (isSaving) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = stringResource(R.string.warehouses_action_saving),
                        )
                    }
                } else {
                    Text(text = stringResource(R.string.warehouses_action_save))
                }
            }
        }
    }
}
