package superapps.minegocio.ui.reportsscreen.dailycashclosure

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
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
            runCatching {
                val warehouses = repository.fetchWarehouses()
                val selectedWarehouseId = warehouses.firstOrNull()?.id
                val summary = repository.fetchDailySummary(
                    warehouseId = selectedWarehouseId,
                    zoneId = clock.zone,
                )
                DailyCashClosureInitialPayload(
                    warehouses = warehouses,
                    selectedWarehouseId = selectedWarehouseId,
                    summary = summary,
                )
            }
                .onSuccess { payload ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            warehouses = payload.warehouses,
                            selectedWarehouseId = payload.selectedWarehouseId,
                            summary = payload.summary,
                            reportDate = LocalDate.now(clock),
                        )
                    }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            warehouses = emptyList(),
                            selectedWarehouseId = null,
                            summary = DailyCashClosureSummary(),
                            reportDate = LocalDate.now(clock),
                            errorMessage = throwable.message ?: throwable.toString(),
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
            runCatching {
                repository.fetchDailySummary(
                    warehouseId = warehouseId,
                    zoneId = clock.zone,
                )
            }
                .onSuccess { summary ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            summary = summary,
                            reportDate = LocalDate.now(clock),
                        )
                    }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            selectedWarehouseId = null,
                            summary = DailyCashClosureSummary(),
                            reportDate = LocalDate.now(clock),
                            errorMessage = throwable.message ?: throwable.toString(),
                        )
                    }
                }
        }
    }
}

private data class DailyCashClosureInitialPayload(
    val warehouses: List<DailyCashClosureWarehouse>,
    val selectedWarehouseId: Long?,
    val summary: DailyCashClosureSummary,
)
