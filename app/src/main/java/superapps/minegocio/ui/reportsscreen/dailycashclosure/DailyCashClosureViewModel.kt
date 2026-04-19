package superapps.minegocio.ui.reportsscreen.dailycashclosure

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import superapps.minegocio.ui.WorkspaceScopeInvalidationBus
import superapps.minegocio.ui.workspacesession.WorkspaceSelectionStore
import java.time.Clock
import java.time.LocalDate

data class DailyCashClosureUiState(
    val isLoading: Boolean = true,
    val warehouses: List<DailyCashClosureWarehouse> = emptyList(),
    val selectedWarehouseId: Long? = null,
    val summary: DailyCashClosureSummary = DailyCashClosureSummary(),
    val reportDate: LocalDate = LocalDate.now(),
    val errorMessage: String? = null,
)

class DailyCashClosureViewModel(
    private val repository: DailyCashClosureRepository = DailyCashClosureRepository(),
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(DailyCashClosureUiState(reportDate = LocalDate.now(clock)))
    val uiState: StateFlow<DailyCashClosureUiState> = _uiState.asStateFlow()
    private var activeWorkspaceId: String? = WorkspaceSelectionStore.selectedWorkspaceId

    init {
        loadInitial()
        observeWorkspaceChanges()
    }

    private fun observeWorkspaceChanges() {
        viewModelScope.launch {
            WorkspaceScopeInvalidationBus.workspaceChanges.collectLatest { workspaceId ->
                if (workspaceId == activeWorkspaceId) return@collectLatest
                activeWorkspaceId = workspaceId
                loadInitial()
            }
        }
    }

    fun loadInitial() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val warehousesDeferred = async { repository.fetchWarehouses() }
                val warehouses = warehousesDeferred.await()
                val selectedWarehouseId = warehouses.firstOrNull()?.id
                val summary = repository.fetchDailySummary(
                    warehouseId = selectedWarehouseId,
                    zoneId = clock.zone,
                )
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        warehouses = warehouses,
                        selectedWarehouseId = selectedWarehouseId,
                        summary = summary,
                        reportDate = LocalDate.now(clock),
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        warehouses = emptyList(),
                        selectedWarehouseId = null,
                        summary = DailyCashClosureSummary(),
                        reportDate = LocalDate.now(clock),
                        errorMessage = e.message ?: e.toString(),
                    )
                }
            }
        }
    }

    fun selectWarehouse(warehouseId: Long) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    selectedWarehouseId = warehouseId,
                    errorMessage = null,
                )
            }
            try {
                val summary = repository.fetchDailySummary(
                    warehouseId = warehouseId,
                    zoneId = clock.zone,
                )
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        summary = summary,
                        reportDate = LocalDate.now(clock),
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        selectedWarehouseId = null,
                        summary = DailyCashClosureSummary(),
                        reportDate = LocalDate.now(clock),
                        errorMessage = e.message ?: e.toString(),
                    )
                }
            }
        }
    }
}
