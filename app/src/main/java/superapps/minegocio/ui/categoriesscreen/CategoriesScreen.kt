package superapps.minegocio.ui.categoriesscreen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
fun CategoriesScreen(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CategoriesViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var isCreateSheetOpen by rememberSaveable { mutableStateOf(false) }
    var categoryName by rememberSaveable { mutableStateOf("") }
    var categoryDescription by rememberSaveable { mutableStateOf("") }
    var isNameTouched by rememberSaveable { mutableStateOf(false) }
    var submitAttempted by rememberSaveable { mutableStateOf(false) }
    var editingCategoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editSubmitAttempted by remember { mutableStateOf(false) }
    val isNameInvalid = isNameTouched && categoryName.isBlank()
    val editingCategory = editingCategoryId?.let { id ->
        uiState.categories.find { it.id == id }
    }
    val editCategoryContentDescription = stringResource(R.string.cd_edit_category)
    val resetCreateState = {
        isCreateSheetOpen = false
        submitAttempted = false
        categoryName = ""
        categoryDescription = ""
        isNameTouched = false
        viewModel.clearCreateError()
    }
    val resetEditState = {
        editingCategoryId = null
        editSubmitAttempted = false
        viewModel.clearUpdateError()
    }

    BackHandler(onBack = onNavigateUp)

    LaunchedEffect(uiState.activeWorkspaceId) {
        resetCreateState()
        resetEditState()
    }

    LaunchedEffect(editingCategoryId, uiState.categories) {
        val id = editingCategoryId ?: return@LaunchedEffect
        if (uiState.categories.none { it.id == id }) {
            editingCategoryId = null
            viewModel.clearUpdateError()
        }
    }

    LaunchedEffect(submitAttempted, uiState.isCreatingCategory, uiState.createErrorMessage) {
        if (submitAttempted && !uiState.isCreatingCategory) {
            if (uiState.createErrorMessage == null) {
                isCreateSheetOpen = false
                categoryName = ""
                categoryDescription = ""
                isNameTouched = false
            }
            submitAttempted = false
        }
    }

    LaunchedEffect(editSubmitAttempted, uiState.isUpdatingCategory, uiState.updateErrorMessage) {
        if (editSubmitAttempted && !uiState.isUpdatingCategory) {
            if (uiState.updateErrorMessage == null) {
                editingCategoryId = null
            }
            editSubmitAttempted = false
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.categories_screen_title))
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateUp,
                    ) {
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
                            contentDescription = stringResource(R.string.cd_add_category),
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
                            items = uiState.categories,
                            key = { it.id },
                        ) { category ->
                            ElevatedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics {
                                        contentDescription = editCategoryContentDescription
                                    }
                                    .clickable {
                                        editingCategoryId = category.id
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
                                        text = category.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val desc = category.description
                                    if (!desc.isNullOrBlank()) {
                                        Text(
                                            text = desc,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 3,
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
                    resetCreateState()
                },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.categories_add_sheet_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = stringResource(R.string.categories_add_sheet_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    OutlinedTextField(
                        value = categoryName,
                        onValueChange = {
                            categoryName = it
                            isNameTouched = true
                            if (uiState.createErrorMessage != null) {
                                viewModel.clearCreateError()
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
                            viewModel.createCategory(categoryName, categoryDescription)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        enabled = !uiState.isCreatingCategory && categoryName.isNotBlank(),
                    ) {
                        if (uiState.isCreatingCategory) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Text(
                                    text = stringResource(R.string.categories_action_creating),
                                )
                            }
                        } else {
                            Text(text = stringResource(R.string.categories_action_add))
                        }
                    }
                }
            }
        }

        editingCategory?.let { category ->
            CategoryEditBottomSheet(
                category = category,
                onDismissRequest = {
                    resetEditState()
                },
                onSave = { name, description ->
                    editSubmitAttempted = true
                    viewModel.updateCategory(category.id, name, description)
                },
                isSaving = uiState.isUpdatingCategory,
                errorMessage = uiState.updateErrorMessage,
                onClearError = { viewModel.clearUpdateError() },
            )
        }
    }
}
