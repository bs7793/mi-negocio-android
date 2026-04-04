package superapps.minegocio.ui.categoriesscreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CategoriesUiState(
    val isLoading: Boolean = true,
    val categories: List<Category> = emptyList(),
    val errorMessage: String? = null,
    val isCreatingCategory: Boolean = false,
    val createErrorMessage: String? = null,
)

class CategoriesViewModel(
    private val repository: CategoriesRepository = CategoriesRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoriesUiState())
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val list = repository.fetchCategories()
                _uiState.update {
                    it.copy(isLoading = false, categories = list, errorMessage = null)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: e.toString(),
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
}
