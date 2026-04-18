package superapps.minegocio.ui.dashboardscreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.YearMonth

private const val ALL_WAREHOUSES_OPTION_ID: Long = -1L

data class DashboardUiState(
    val isLoading: Boolean = true,
    val warehouses: List<DashboardWarehouse> = emptyList(),
    val selectedWarehouseId: Long = ALL_WAREHOUSES_OPTION_ID,
    val summary: DashboardIncomeStatementSummary = DashboardIncomeStatementSummary(),
    val period: YearMonth = YearMonth.now(),
    val errorMessage: String? = null,
)

class DashboardViewModel(
    private val repository: DashboardRepository = DashboardRepository(),
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState(period = YearMonth.now(clock)))
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadInitial()
    }

    fun loadInitial() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val warehousesDeferred = async { repository.fetchWarehouses() }
                val summaryDeferred = async { repository.fetchMonthlySummary(warehouseId = null, zoneId = clock.zone) }
                val warehouses = warehousesDeferred.await()
                val summary = summaryDeferred.await()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        warehouses = warehouses,
                        selectedWarehouseId = ALL_WAREHOUSES_OPTION_ID,
                        summary = summary,
                        period = YearMonth.now(clock),
                    )
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
                val summary = repository.fetchMonthlySummary(
                    warehouseId = warehouseId.takeUnless { it == ALL_WAREHOUSES_OPTION_ID },
                    zoneId = clock.zone,
                )
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        summary = summary,
                        period = YearMonth.now(clock),
                    )
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
}
