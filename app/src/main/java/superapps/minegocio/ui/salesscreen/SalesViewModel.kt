package superapps.minegocio.ui.salesscreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import superapps.minegocio.ui.SalesSummaryInvalidationBus
import superapps.minegocio.ui.WorkspaceScopeInvalidationBus
import superapps.minegocio.ui.warehousesscreen.Warehouse
import superapps.minegocio.ui.workspacesession.WorkspaceSelectionStore
import kotlin.math.max

data class SalesUiState(
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val variants: List<SellableVariant> = emptyList(),
    val cartItems: List<SaleCartItem> = emptyList(),
    val warehouses: List<Warehouse> = emptyList(),
    val selectedWarehouseId: Long? = null,
    val searchQuery: String = "",
    val hasSearched: Boolean = false,
    val customerNameInput: String = "Public",
    val notesInput: String = "",
    val paymentDraft: SalesPaymentDraft = SalesPaymentDraft(),
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val activeWorkspaceId: String? = null,
) {
    val cartTotal: Double
        get() = cartItems.sumOf { it.lineTotal }
}

class SalesViewModel(
    private val repository: SalesRepository = SalesRepository(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(SalesUiState())
    val uiState: StateFlow<SalesUiState> = _uiState.asStateFlow()
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
                _uiState.update {
                    it.copy(
                        cartItems = emptyList(),
                        searchQuery = "",
                        hasSearched = false,
                        customerNameInput = "Public",
                        notesInput = "",
                        paymentDraft = SalesPaymentDraft(),
                        errorMessage = null,
                        successMessage = null,
                        activeWorkspaceId = workspaceId,
                    )
                }
                loadInitial()
            }
        }
    }

    fun loadInitial() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            runCatching {
                val warehouses = repository.fetchWarehouses()
                val selectedWarehouseId = warehouses.firstOrNull()?.id
                val variants = repository.fetchSellableVariants(
                    search = null,
                    warehouseId = selectedWarehouseId,
                )
                SalesInitialPayload(
                    warehouses = warehouses,
                    selectedWarehouseId = selectedWarehouseId,
                    variants = variants.items,
                )
            }
                .onSuccess { payload ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            warehouses = payload.warehouses,
                            selectedWarehouseId = payload.selectedWarehouseId,
                            variants = payload.variants,
                            paymentDraft = it.paymentDraft.copy(amountInput = formatAmount(it.cartTotal)),
                            activeWorkspaceId = activeWorkspaceId,
                        )
                    }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = throwable.message ?: throwable.toString(),
                        )
                    }
                }
        }
    }

    fun setSearchQuery(value: String) {
        _uiState.update { it.copy(searchQuery = value) }
    }

    fun updateCustomerName(value: String) {
        _uiState.update { it.copy(customerNameInput = value) }
    }

    fun updateNotes(value: String) {
        _uiState.update { it.copy(notesInput = value) }
    }

    fun updatePaymentMethod(value: String) {
        _uiState.update {
            it.copy(
                paymentDraft = it.paymentDraft.copy(method = value),
                errorMessage = null,
                successMessage = null,
            )
        }
    }

    fun updatePaymentAmount(value: String) {
        _uiState.update {
            it.copy(
                paymentDraft = it.paymentDraft.copy(amountInput = value),
                errorMessage = null,
                successMessage = null,
            )
        }
    }

    fun updatePaymentReference(value: String) {
        _uiState.update {
            it.copy(
                paymentDraft = it.paymentDraft.copy(reference = value),
                errorMessage = null,
                successMessage = null,
            )
        }
    }

    fun selectWarehouse(warehouseId: Long) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedWarehouseId = warehouseId,
                    isLoading = true,
                    errorMessage = null,
                    successMessage = null,
                )
            }
            runCatching {
                repository.fetchSellableVariants(
                    search = _uiState.value.searchQuery,
                    warehouseId = warehouseId,
                )
            }
                .onSuccess { variants ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            variants = variants.items,
                        )
                    }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = throwable.message ?: throwable.toString(),
                        )
                    }
                }
        }
    }

    fun searchVariants() {
        viewModelScope.launch {
            val warehouseId = _uiState.value.selectedWarehouseId
            _uiState.update {
                it.copy(
                    isLoading = true,
                    hasSearched = true,
                    errorMessage = null,
                    successMessage = null,
                )
            }
            runCatching {
                repository.fetchSellableVariants(
                    search = _uiState.value.searchQuery,
                    warehouseId = warehouseId,
                )
            }
                .onSuccess { variants ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            variants = variants.items,
                        )
                    }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = throwable.message ?: throwable.toString(),
                        )
                    }
                }
        }
    }

    fun clearSearchAndReload() {
        viewModelScope.launch {
            val warehouseId = _uiState.value.selectedWarehouseId
            _uiState.update {
                it.copy(
                    searchQuery = "",
                    hasSearched = false,
                    isLoading = true,
                    errorMessage = null,
                    successMessage = null,
                )
            }
            runCatching {
                repository.fetchSellableVariants(
                    search = null,
                    warehouseId = warehouseId,
                )
            }
                .onSuccess { variants ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            variants = variants.items,
                        )
                    }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = throwable.message ?: throwable.toString(),
                        )
                    }
                }
        }
    }

    fun addVariantToCart(variant: SellableVariant) {
        _uiState.update { current ->
            val existing = current.cartItems.firstOrNull { it.variant.variantId == variant.variantId }
            val updatedItems = if (existing == null) {
                current.cartItems + SaleCartItem(variant = variant)
            } else {
                current.cartItems.map { item ->
                    if (item.variant.variantId != variant.variantId) {
                        item
                    } else {
                        val nextQuantity = item.quantity + 1
                        item.copy(quantityInput = formatQuantity(nextQuantity))
                    }
                }
            }
            val total = updatedItems.sumOf { it.lineTotal }
            current.copy(
                cartItems = updatedItems,
                paymentDraft = current.paymentDraft.copy(
                    amountInput = formatAmount(total),
                ),
                errorMessage = null,
                successMessage = null,
            )
        }
    }

    fun incrementQuantity(variantId: Long) {
        updateCartItem(variantId) { item ->
            val nextQuantity = item.quantity + 1
            item.copy(quantityInput = formatQuantity(nextQuantity))
        }
    }

    fun decrementQuantity(variantId: Long) {
        updateCartItem(variantId) { item ->
            val currentQuantity = item.quantity
            val nextQuantity = max(currentQuantity - 1, 0.001)
            item.copy(quantityInput = formatQuantity(nextQuantity))
        }
    }

    fun updateQuantity(variantId: Long, value: String) {
        updateCartItem(variantId) { item ->
            item.copy(quantityInput = value)
        }
    }

    fun updateUnitPrice(variantId: Long, value: String) {
        updateCartItem(variantId) { item ->
            item.copy(unitPriceInput = value)
        }
    }

    fun removeFromCart(variantId: Long) {
        _uiState.update { current ->
            val updatedItems = current.cartItems.filterNot { it.variant.variantId == variantId }
            current.copy(
                cartItems = updatedItems,
                paymentDraft = current.paymentDraft.copy(
                    amountInput = if (updatedItems.isEmpty()) "" else formatAmount(updatedItems.sumOf { it.lineTotal }),
                ),
                errorMessage = null,
                successMessage = null,
            )
        }
    }

    fun checkout() {
        viewModelScope.launch {
            val state = _uiState.value
            val warehouseId = state.selectedWarehouseId
            if (warehouseId == null) {
                _uiState.update { it.copy(errorMessage = "Select a warehouse before checkout.") }
                return@launch
            }
            if (state.cartItems.isEmpty()) {
                _uiState.update { it.copy(errorMessage = "Add at least one item to the sale.") }
                return@launch
            }

            val lineInputs = state.cartItems.mapNotNull { item ->
                val quantity = item.quantity
                val unitPrice = item.unitPrice
                if (quantity <= 0 || unitPrice < 0) {
                    null
                } else {
                    SaleCreateLineInput(
                        variantId = item.variant.variantId,
                        quantity = quantity,
                        appliedUnitPrice = unitPrice,
                        appliedCostPrice = item.variant.costPrice,
                    )
                }
            }
            if (lineInputs.size != state.cartItems.size) {
                _uiState.update { it.copy(errorMessage = "Check quantities and prices before checkout.") }
                return@launch
            }

            val payment = state.paymentDraft
            if (payment.amount <= 0) {
                _uiState.update { it.copy(errorMessage = "Enter a valid payment amount.") }
                return@launch
            }

            _uiState.update { it.copy(isSubmitting = true, errorMessage = null, successMessage = null) }
            try {
                val response = repository.createSale(
                    warehouseId = warehouseId,
                    customerName = state.customerNameInput,
                    notes = state.notesInput,
                    lines = lineInputs,
                    payments = listOf(
                        SaleCreatePaymentInput(
                            method = payment.method,
                            amount = payment.amount,
                            reference = payment.reference.takeIf { it.isNotBlank() },
                        ),
                    ),
                )

                val refreshedVariants = repository.fetchSellableVariants(
                    search = state.searchQuery,
                    warehouseId = warehouseId,
                )

                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        variants = refreshedVariants.items,
                        cartItems = emptyList(),
                        paymentDraft = it.paymentDraft.copy(amountInput = "", reference = ""),
                        errorMessage = null,
                        successMessage = "Sale #${response.saleId} completed.",
                    )
                }
                SalesSummaryInvalidationBus.invalidateSalesSummary()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = e.message ?: e.toString(),
                    )
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    private fun updateCartItem(
        variantId: Long,
        update: (SaleCartItem) -> SaleCartItem,
    ) {
        _uiState.update { current ->
            val updatedItems = current.cartItems.map { item ->
                if (item.variant.variantId == variantId) update(item) else item
            }
            current.copy(
                cartItems = updatedItems,
                paymentDraft = current.paymentDraft.copy(
                    amountInput = if (updatedItems.isEmpty()) "" else formatAmount(updatedItems.sumOf { it.lineTotal }),
                ),
                errorMessage = null,
                successMessage = null,
            )
        }
    }

    private fun formatQuantity(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            String.format("%.3f", value).trimEnd('0').trimEnd('.')
        }
    }

    private fun formatAmount(value: Double): String {
        return String.format("%.2f", value)
    }
}

private data class SalesInitialPayload(
    val warehouses: List<Warehouse>,
    val selectedWarehouseId: Long?,
    val variants: List<SellableVariant>,
)
