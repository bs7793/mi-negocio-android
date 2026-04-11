package superapps.minegocio.ui.categoriesscreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
fun CategoryEditBottomSheet(
    category: Category,
    onDismissRequest: () -> Unit,
    onSave: (name: String, description: String?) -> Unit,
    isSaving: Boolean,
    errorMessage: String?,
    onClearError: () -> Unit,
) {
    var categoryName by remember { mutableStateOf("") }
    var categoryDescription by remember { mutableStateOf("") }
    var isNameTouched by remember { mutableStateOf(false) }

    LaunchedEffect(category.id) {
        categoryName = category.name
        categoryDescription = category.description.orEmpty()
        isNameTouched = false
    }

    val isNameInvalid = isNameTouched && categoryName.isBlank()

    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.categories_edit_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.categories_edit_sheet_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = categoryName,
                onValueChange = {
                    categoryName = it
                    isNameTouched = true
                    if (errorMessage != null) {
                        onClearError()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.categories_field_name)) },
                placeholder = { Text(stringResource(R.string.categories_field_name_placeholder)) },
                singleLine = true,
                isError = isNameInvalid,
            )
            if (isNameInvalid) {
                Text(
                    text = stringResource(R.string.categories_field_name_required),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            OutlinedTextField(
                value = categoryDescription,
                onValueChange = { categoryDescription = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.categories_field_description)) },
                placeholder = {
                    Text(stringResource(R.string.categories_field_description_placeholder))
                },
                minLines = 3,
                maxLines = 5,
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
                    if (categoryName.isNotBlank()) {
                        onSave(
                            categoryName,
                            categoryDescription.trim().takeUnless { it.isEmpty() },
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                enabled = !isSaving && categoryName.isNotBlank(),
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
                            text = stringResource(R.string.categories_action_saving),
                        )
                    }
                } else {
                    Text(text = stringResource(R.string.categories_action_save))
                }
            }
        }
    }
}
