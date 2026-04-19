package superapps.minegocio.ui.categoriesscreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import superapps.minegocio.ui.WorkspaceScopeInvalidationBus
import superapps.minegocio.ui.workspacesession.WorkspaceSelectionStore

data class CategoriesUiState(
    val isLoading: Boolean = true,
    val categories: List<Category> = emptyList(),
    val errorMessage: String? = null,
    val isCreatingCategory: Boolean = false,
    val createErrorMessage: String? = null,
    val isUpdatingCategory: Boolean = false,
    val updateErrorMessage: String? = null,
    val activeWorkspaceId: String? = null,
)

class CategoriesViewModel(
    private val repository: CategoriesRepository = CategoriesRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoriesUiState())
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()
    private var activeWorkspaceId: String? = WorkspaceSelectionStore.selectedWorkspaceId

    init {
        load()
        observeWorkspaceChanges()
    }

    private fun observeWorkspaceChanges() {
        viewModelScope.launch {
            WorkspaceScopeInvalidationBus.workspaceChanges.collectLatest { workspaceId ->
                if (workspaceId == activeWorkspaceId) return@collectLatest
                activeWorkspaceId = workspaceId
                _uiState.update { it.copy(activeWorkspaceId = workspaceId) }
                load()
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            val workspaceId = activeWorkspaceId
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    activeWorkspaceId = workspaceId,
                )
            }
            try {
                val list = repository.fetchCategories()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        categories = list,
                        errorMessage = null,
                        activeWorkspaceId = workspaceId,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: e.toString(),
                        activeWorkspaceId = workspaceId,
                    )
                }
            }
        }
    }

    fun createCategory(name: String, description: String?) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            _uiState.update { it.copy(createErrorMessage = null) }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(isCreatingCategory = true, createErrorMessage = null)
            }
            try {
                val createdCategory = repository.createCategory(
                    name = trimmedName,
                    description = description,
                )
                _uiState.update {
                    it.copy(
                        isCreatingCategory = false,
                        createErrorMessage = null,
                        categories = listOf(createdCategory) + it.categories,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isCreatingCategory = false,
                        createErrorMessage = e.message ?: e.toString(),
                    )
                }
            }
        }
    }

    fun clearCreateError() {
        _uiState.update { it.copy(createErrorMessage = null) }
    }

    fun updateCategory(id: Long, name: String, description: String?) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            _uiState.update { it.copy(updateErrorMessage = null) }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(isUpdatingCategory = true, updateErrorMessage = null)
            }
            try {
                val updated = repository.updateCategory(
                    id = id,
                    name = trimmedName,
                    description = description,
                )
                _uiState.update { state ->
                    state.copy(
                        isUpdatingCategory = false,
                        updateErrorMessage = null,
                        categories = state.categories.map { c ->
                            if (c.id == id) updated else c
                        },
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isUpdatingCategory = false,
                        updateErrorMessage = e.message ?: e.toString(),
                    )
                }
            }
        }
    }

    fun clearUpdateError() {
        _uiState.update { it.copy(updateErrorMessage = null) }
    }
}
