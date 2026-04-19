package superapps.minegocio.ui.dashboardscreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import superapps.minegocio.ui.SalesSummaryInvalidationBus
import superapps.minegocio.ui.WorkspaceScopeInvalidationBus
import superapps.minegocio.ui.workspacesession.WorkspaceSelectionStore
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
    private var activeWorkspaceId: String? = WorkspaceSelectionStore.selectedWorkspaceId
    private var latestRefreshRequestId: Long = 0
    private var refreshingWarehouseFilter: Long? = null

    init {
        loadInitial()
        viewModelScope.launch {
            SalesSummaryInvalidationBus.salesSummaryInvalidations.collectLatest {
                requestSummaryRefresh(force = false)
            }
        }
        viewModelScope.launch {
            WorkspaceScopeInvalidationBus.workspaceChanges.collectLatest { workspaceId ->
                if (workspaceId == activeWorkspaceId) return@collectLatest
                activeWorkspaceId = workspaceId
                closeSaleDetail()
                loadInitial()
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
            supervisorScope {
                val feedDeferred = async {
                    runCatching {
                        repository.fetchSalesFeed(warehouseId = null, zoneId = clock.zone)
                    }
                }
                val primaryResult = runCatching {
                    val warehouses = repository.fetchWarehouses()
                    val summary = repository.fetchMonthlySummary(warehouseId = null, zoneId = clock.zone)
                    warehouses to summary
                }
                val feedResult = feedDeferred.await()
                primaryResult
                    .onSuccess { (warehouses, summary) ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isSummaryUpdating = false,
                                isRefreshing = false,
                                warehouses = warehouses,
                                selectedWarehouseId = ALL_WAREHOUSES_OPTION_ID,
                                summary = summary,
                                salesFeed = feedResult.getOrElse { emptyList() },
                                feedErrorMessage = feedResult.exceptionOrNull()?.asUiMessage(),
                                period = YearMonth.now(clock),
                                errorMessage = null,
                            )
                        }
                    }
                    .onFailure { throwable ->
                        if (throwable is CancellationException) throw throwable
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isSummaryUpdating = false,
                                isRefreshing = false,
                                errorMessage = throwable.asUiMessage(),
                                salesFeed = feedResult.getOrElse { emptyList() },
                                feedErrorMessage = feedResult.exceptionOrNull()?.asUiMessage(),
                            )
                        }
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
            supervisorScope {
                val feedDeferred = async {
                    runCatching {
                        repository.fetchSalesFeed(warehouseId = warehouseFilter, zoneId = clock.zone)
                    }
                }
                val summaryResult = runCatching {
                    repository.fetchMonthlySummary(warehouseId = warehouseFilter, zoneId = clock.zone)
                }
                val feedResult = feedDeferred.await()
                if (requestId != latestRefreshRequestId) return@supervisorScope
                summaryResult
                    .onSuccess { summary ->
                        _uiState.update {
                            it.copy(
                                isRefreshing = false,
                                summary = summary,
                                salesFeed = feedResult.getOrElse { emptyList() },
                                feedErrorMessage = feedResult.exceptionOrNull()?.asUiMessage(),
                                period = YearMonth.now(clock),
                                errorMessage = null,
                            )
                        }
                    }
                    .onFailure { throwable ->
                        if (throwable is CancellationException) throw throwable
                        _uiState.update {
                            it.copy(
                                isRefreshing = false,
                                errorMessage = throwable.asUiMessage(),
                                salesFeed = feedResult.getOrElse { emptyList() },
                                feedErrorMessage = feedResult.exceptionOrNull()?.asUiMessage(),
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
            supervisorScope {
                val warehouseFilter = warehouseId.takeUnless { it == ALL_WAREHOUSES_OPTION_ID }
                val feedDeferred = async {
                    runCatching {
                        repository.fetchSalesFeed(
                            warehouseId = warehouseFilter,
                            zoneId = clock.zone,
                        )
                    }
                }
                val summaryResult = runCatching {
                    repository.fetchMonthlySummary(
                        warehouseId = warehouseFilter,
                        zoneId = clock.zone,
                    )
                }
                val feedResult = feedDeferred.await()
                summaryResult
                    .onSuccess { summary ->
                        _uiState.update {
                            it.copy(
                                isSummaryUpdating = false,
                                isLoading = false,
                                isRefreshing = false,
                                summary = summary,
                                salesFeed = feedResult.getOrElse { emptyList() },
                                feedErrorMessage = feedResult.exceptionOrNull()?.asUiMessage(),
                                period = YearMonth.now(clock),
                            )
                        }
                    }
                    .onFailure { throwable ->
                        if (throwable is CancellationException) throw throwable
                        _uiState.update {
                            it.copy(
                                isSummaryUpdating = false,
                                isLoading = false,
                                isRefreshing = false,
                                errorMessage = throwable.asUiMessage(),
                                salesFeed = feedResult.getOrElse { emptyList() },
                                feedErrorMessage = feedResult.exceptionOrNull()?.asUiMessage(),
                            )
                        }
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

private fun Throwable.asUiMessage(): String = message ?: toString()
