package superapps.minegocio.ui.dashboardscreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import superapps.minegocio.ui.SalesSummaryInvalidationBus
import java.time.Clock
import java.time.YearMonth

private const val ALL_WAREHOUSES_OPTION_ID: Long = -1L

data class DashboardUiState(
    val isLoading: Boolean = true,
    /** True while fetching summary after warehouse change; keeps previous KPIs visible. */
    val isSummaryUpdating: Boolean = false,
    val isRefreshing: Boolean = false,
    val warehouses: List<DashboardWarehouse> = emptyList(),
    val selectedWarehouseId: Long = ALL_WAREHOUSES_OPTION_ID,
    val summary: DashboardIncomeStatementSummary = DashboardIncomeStatementSummary(),
    val salesFeed: List<DashboardSalesFeedItem> = emptyList(),
    val period: YearMonth = YearMonth.now(),
    val errorMessage: String? = null,
    /** Non-blocking: KPI can load while feed fails. */
    val feedErrorMessage: String? = null,
    val selectedSaleId: Long? = null,
    val isSaleDetailLoading: Boolean = false,
    val saleDetail: DashboardSaleDetail? = null,
    val saleDetailError: String? = null,
    val isReceiptGenerating: Boolean = false,
    val receiptShareUrl: String? = null,
    val receiptErrorMessage: String? = null,
)

class DashboardViewModel(
    private val repository: DashboardRepository = DashboardRepository(),
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState(period = YearMonth.now(clock)))
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()
    private var refreshJob: Job? = null
    private var saleDetailJob: Job? = null
    private var receiptJob: Job? = null
    private var latestRefreshRequestId: Long = 0
    private var refreshingWarehouseFilter: Long? = null

    init {
        loadInitial()
        viewModelScope.launch {
            SalesSummaryInvalidationBus.salesSummaryInvalidations.collectLatest {
                requestSummaryRefresh(force = false)
            }
        }
    }

    fun loadInitial() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    isSummaryUpdating = false,
                    isRefreshing = false,
                    errorMessage = null,
                    feedErrorMessage = null,
                )
            }
            val warehousesDeferred = async { repository.fetchWarehouses() }
            val summaryDeferred = async { repository.fetchMonthlySummary(warehouseId = null, zoneId = clock.zone) }
            val feedDeferred = async { runCatching { repository.fetchSalesFeed(warehouseId = null, zoneId = clock.zone) } }
            try {
                val warehouses = warehousesDeferred.await()
                val summary = summaryDeferred.await()
                val feedResult = feedDeferred.await()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isSummaryUpdating = false,
                        isRefreshing = false,
                        warehouses = warehouses,
                        selectedWarehouseId = ALL_WAREHOUSES_OPTION_ID,
                        summary = summary,
                        salesFeed = feedResult.getOrElse { emptyList() },
                        feedErrorMessage = feedResult.exceptionOrNull()?.let { e ->
                            e.message ?: e.toString()
                        },
                        period = YearMonth.now(clock),
                        errorMessage = null,
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                val feedResult = feedDeferred.await()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isSummaryUpdating = false,
                        isRefreshing = false,
                        errorMessage = e.message ?: e.toString(),
                        salesFeed = feedResult.getOrElse { emptyList() },
                        feedErrorMessage = feedResult.exceptionOrNull()?.let { err ->
                            err.message ?: err.toString()
                        },
                    )
                }
            }
        }
    }

    private fun requestSummaryRefresh(force: Boolean) {
        val warehouseFilter = _uiState.value.selectedWarehouseId.takeUnless { it == ALL_WAREHOUSES_OPTION_ID }
        if (!force && _uiState.value.isRefreshing && refreshingWarehouseFilter == warehouseFilter) {
            return
        }
        latestRefreshRequestId += 1
        val requestId = latestRefreshRequestId
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            refreshSummaryStaleWhileRevalidate(
                warehouseFilter = warehouseFilter,
                requestId = requestId,
            )
        }
    }

    private suspend fun refreshSummaryStaleWhileRevalidate(
        warehouseFilter: Long?,
        requestId: Long,
    ) {
        refreshingWarehouseFilter = warehouseFilter
        _uiState.update { it.copy(isRefreshing = true) }
        try {
            coroutineScope {
                val summaryDeferred = async { repository.fetchMonthlySummary(warehouseId = warehouseFilter, zoneId = clock.zone) }
                val feedDeferred = async { runCatching { repository.fetchSalesFeed(warehouseId = warehouseFilter, zoneId = clock.zone) } }
                try {
                    val summary = summaryDeferred.await()
                    val feedResult = feedDeferred.await()
                    if (requestId != latestRefreshRequestId) return@coroutineScope
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            summary = summary,
                            salesFeed = feedResult.getOrElse { emptyList() },
                            feedErrorMessage = feedResult.exceptionOrNull()?.let { e -> e.message ?: e.toString() },
                            period = YearMonth.now(clock),
                            errorMessage = null,
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    if (requestId != latestRefreshRequestId) return@coroutineScope
                    val feedResult = feedDeferred.await()
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            errorMessage = e.message ?: e.toString(),
                            salesFeed = feedResult.getOrElse { emptyList() },
                            feedErrorMessage = feedResult.exceptionOrNull()?.let { err -> err.message ?: err.toString() },
                        )
                    }
                }
            }
        } finally {
            if (requestId == latestRefreshRequestId) {
                refreshingWarehouseFilter = null
            }
        }
    }

    fun refresh() {
        requestSummaryRefresh(force = false)
    }

    fun selectWarehouse(warehouseId: Long) {
        viewModelScope.launch {
            latestRefreshRequestId += 1
            refreshJob?.cancel()
            refreshingWarehouseFilter = null
            _uiState.update {
                it.copy(
                    isSummaryUpdating = true,
                    isLoading = false,
                    isRefreshing = false,
                    selectedWarehouseId = warehouseId,
                    errorMessage = null,
                    feedErrorMessage = null,
                )
            }
            val summaryDeferred = async {
                repository.fetchMonthlySummary(
                    warehouseId = warehouseId.takeUnless { it == ALL_WAREHOUSES_OPTION_ID },
                    zoneId = clock.zone,
                )
            }
            val feedDeferred = async {
                runCatching {
                    repository.fetchSalesFeed(
                        warehouseId = warehouseId.takeUnless { it == ALL_WAREHOUSES_OPTION_ID },
                        zoneId = clock.zone,
                    )
                }
            }
            try {
                val summary = summaryDeferred.await()
                val feedResult = feedDeferred.await()
                _uiState.update {
                    it.copy(
                        isSummaryUpdating = false,
                        isLoading = false,
                        isRefreshing = false,
                        summary = summary,
                        salesFeed = feedResult.getOrElse { emptyList() },
                        feedErrorMessage = feedResult.exceptionOrNull()?.let { e -> e.message ?: e.toString() },
                        period = YearMonth.now(clock),
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                val feedResult = feedDeferred.await()
                _uiState.update {
                    it.copy(
                        isSummaryUpdating = false,
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = e.message ?: e.toString(),
                        salesFeed = feedResult.getOrElse { emptyList() },
                        feedErrorMessage = feedResult.exceptionOrNull()?.let { err ->
                            err.message ?: err.toString()
                        },
                    )
                }
            }
        }
    }

    fun openSaleDetail(saleId: Long) {
        saleDetailJob?.cancel()
        receiptJob?.cancel()
        receiptJob = null
        saleDetailJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedSaleId = saleId,
                    isSaleDetailLoading = true,
                    saleDetail = null,
                    saleDetailError = null,
                    isReceiptGenerating = false,
                    receiptShareUrl = null,
                    receiptErrorMessage = null,
                )
            }
            try {
                val detail = repository.fetchSaleDetail(saleId)
                ensureActive()
                if (_uiState.value.selectedSaleId != saleId) return@launch
                _uiState.update {
                    it.copy(
                        isSaleDetailLoading = false,
                        saleDetail = detail,
                        saleDetailError = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_uiState.value.selectedSaleId != saleId) return@launch
                _uiState.update {
                    it.copy(
                        isSaleDetailLoading = false,
                        saleDetail = null,
                        saleDetailError = e.message ?: e.toString(),
                    )
                }
            }
        }
    }

    fun closeSaleDetail() {
        saleDetailJob?.cancel()
        saleDetailJob = null
        receiptJob?.cancel()
        receiptJob = null
        _uiState.update {
            it.copy(
                selectedSaleId = null,
                isSaleDetailLoading = false,
                saleDetail = null,
                saleDetailError = null,
                isReceiptGenerating = false,
                receiptShareUrl = null,
                receiptErrorMessage = null,
            )
        }
    }

    fun retrySaleDetail() {
        val id = _uiState.value.selectedSaleId ?: return
        openSaleDetail(id)
    }

    fun createReceiptForSelectedSale() {
        val currentState = _uiState.value
        if (currentState.isReceiptGenerating) return
        val saleId = currentState.saleDetail?.saleId ?: currentState.selectedSaleId ?: return
        receiptJob?.cancel()
        receiptJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isReceiptGenerating = true,
                    receiptShareUrl = null,
                    receiptErrorMessage = null,
                )
            }
            try {
                val shareUrl = repository.createSaleReceipt(saleId)
                _uiState.update {
                    it.copy(
                        isReceiptGenerating = false,
                        receiptShareUrl = shareUrl,
                        receiptErrorMessage = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isReceiptGenerating = false,
                        receiptErrorMessage = e.message ?: e.toString(),
                    )
                }
            }
        }
    }

    fun clearReceiptError() {
        _uiState.update { it.copy(receiptErrorMessage = null) }
    }

    fun onReceiptShared() {
        _uiState.update { it.copy(receiptShareUrl = null) }
    }
}
