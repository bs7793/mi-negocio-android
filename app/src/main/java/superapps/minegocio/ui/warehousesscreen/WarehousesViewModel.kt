package superapps.minegocio.ui.warehousesscreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WarehousesUiState(
    val isLoading: Boolean = true,
    val warehouses: List<Warehouse> = emptyList(),
    val errorMessage: String? = null,
    val isCreatingWarehouse: Boolean = false,
    val createErrorMessage: String? = null,
    val isUpdatingWarehouse: Boolean = false,
    val updateErrorMessage: String? = null,
)

class WarehousesViewModel(
    private val repository: WarehousesRepository = WarehousesRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(WarehousesUiState())
    val uiState: StateFlow<WarehousesUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val list = repository.fetchWarehouses()
                _uiState.update {
                    it.copy(isLoading = false, warehouses = list, errorMessage = null)
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

    fun createWarehouse(
        name: String,
        location: String?,
        aisle: String?,
        shelf: String?,
        level: String?,
        position: String?,
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            _uiState.update { it.copy(createErrorMessage = null) }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(isCreatingWarehouse = true, createErrorMessage = null)
            }
            try {
                val created = repository.createWarehouse(
                    name = trimmedName,
                    location = location,
                    aisle = aisle,
                    shelf = shelf,
                    level = level,
                    position = position,
                )
                _uiState.update {
                    it.copy(
                        isCreatingWarehouse = false,
                        createErrorMessage = null,
                        warehouses = listOf(created) + it.warehouses,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isCreatingWarehouse = false,
                        createErrorMessage = e.message ?: e.toString(),
                    )
                }
            }
        }
    }

    fun clearCreateError() {
        _uiState.update { it.copy(createErrorMessage = null) }
    }

    fun updateWarehouse(
        id: Long,
        name: String,
        location: String?,
        aisle: String?,
        shelf: String?,
        level: String?,
        position: String?,
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            _uiState.update { it.copy(updateErrorMessage = null) }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(isUpdatingWarehouse = true, updateErrorMessage = null)
            }
            try {
                val updated = repository.updateWarehouse(
                    id = id,
                    name = trimmedName,
                    location = location,
                    aisle = aisle,
                    shelf = shelf,
                    level = level,
                    position = position,
                )
                _uiState.update { state ->
                    state.copy(
                        isUpdatingWarehouse = false,
                        updateErrorMessage = null,
                        warehouses = state.warehouses.map { w ->
                            if (w.id == id) updated else w
                        },
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isUpdatingWarehouse = false,
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
