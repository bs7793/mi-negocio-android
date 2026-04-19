package superapps.minegocio.ui.productsscreen

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
import superapps.minegocio.ui.categoriesscreen.Category
import superapps.minegocio.ui.warehousesscreen.Warehouse
import superapps.minegocio.ui.workspacesession.WorkspaceSelectionStore

data class ProductsUiState(
    val isLoading: Boolean = true,
    val products: List<Product> = emptyList(),
    val categories: List<Category> = emptyList(),
    val warehouses: List<Warehouse> = emptyList(),
    val optionTypesCatalog: List<ProductOptionTypeCatalog> = emptyList(),
    val total: Int = 0,
    val searchQuery: String = "",
    val selectedCategoryId: Long? = null,
    val selectedWarehouseId: Long? = null,
    val errorMessage: String? = null,
    val isCreatingProduct: Boolean = false,
    val createErrorMessage: String? = null,
    val isUpdatingProduct: Boolean = false,
    val updateErrorMessage: String? = null,
    val updateWarningMessage: String? = null,
    val activeWorkspaceId: String? = null,
)

class ProductsViewModel(
    private val repository: ProductsRepository = ProductsRepository(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProductsUiState())
    val uiState: StateFlow<ProductsUiState> = _uiState.asStateFlow()
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
                        searchQuery = "",
                        selectedCategoryId = null,
                        selectedWarehouseId = null,
                        errorMessage = null,
                        createErrorMessage = null,
                        updateErrorMessage = null,
                        updateWarningMessage = null,
                        activeWorkspaceId = workspaceId,
                    )
                }
                loadInitial()
            }
        }
    }

    fun loadInitial() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                val categories = repository.fetchCategories()
                val warehouses = repository.fetchWarehouses()
                val optionCatalog = repository.fetchProductOptionsCatalog()
                val products = repository.fetchProducts(
                    search = _uiState.value.searchQuery,
                    categoryId = _uiState.value.selectedCategoryId,
                    warehouseId = _uiState.value.selectedWarehouseId,
                )
                InitialProductsPayload(
                    categories = categories,
                    warehouses = warehouses,
                    optionTypes = optionCatalog.optionTypes,
                    products = products.items,
                    total = products.total,
                )
            }
                .onSuccess { payload ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            categories = payload.categories,
                            warehouses = payload.warehouses,
                            optionTypesCatalog = payload.optionTypes,
                            products = payload.products,
                            total = payload.total,
                            errorMessage = null,
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

    fun refreshProducts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val products = repository.fetchProducts(
                    search = _uiState.value.searchQuery,
                    categoryId = _uiState.value.selectedCategoryId,
                    warehouseId = _uiState.value.selectedWarehouseId,
                )
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        products = products.items,
                        total = products.total,
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

    fun setSearchQuery(value: String) {
        _uiState.update { it.copy(searchQuery = value) }
    }

    fun applyFilters(
        categoryId: Long?,
        warehouseId: Long?,
    ) {
        _uiState.update {
            it.copy(
                selectedCategoryId = categoryId,
                selectedWarehouseId = warehouseId,
            )
        }
        refreshProducts()
    }

    fun submitSearch() {
        refreshProducts()
    }

    fun createProduct(
        payload: CreateProductPayload,
        imageUpload: ProductImageUpload? = null,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingProduct = true, createErrorMessage = null) }
            try {
                repository.createProduct(payload, imageUpload)
                val refreshed = repository.fetchProducts(
                    search = _uiState.value.searchQuery,
                    categoryId = _uiState.value.selectedCategoryId,
                    warehouseId = _uiState.value.selectedWarehouseId,
                )
                val optionCatalog = repository.fetchProductOptionsCatalog()
                _uiState.update {
                    it.copy(
                        isCreatingProduct = false,
                        createErrorMessage = null,
                        optionTypesCatalog = optionCatalog.optionTypes,
                        products = refreshed.items,
                        total = refreshed.total,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isCreatingProduct = false,
                        createErrorMessage = e.message ?: e.toString(),
                    )
                }
            }
        }
    }

    fun clearCreateError() {
        _uiState.update { it.copy(createErrorMessage = null) }
    }

    fun updateProduct(
        payload: UpdateProductBasicPayload,
        imageUpload: ProductImageUpload? = null,
        previousImageUrl: String? = null,
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isUpdatingProduct = true,
                    updateErrorMessage = null,
                    updateWarningMessage = null,
                )
            }
            try {
                val updateResult = repository.updateProductBasic(
                    payload = payload,
                    imageUpload = imageUpload,
                    previousImageUrl = previousImageUrl,
                )
                val refreshed = repository.fetchProducts(
                    search = _uiState.value.searchQuery,
                    categoryId = _uiState.value.selectedCategoryId,
                    warehouseId = _uiState.value.selectedWarehouseId,
                )
                _uiState.update {
                    it.copy(
                        isUpdatingProduct = false,
                        updateErrorMessage = null,
                        updateWarningMessage = updateResult.cleanupWarning,
                        products = refreshed.items,
                        total = refreshed.total,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isUpdatingProduct = false,
                        updateErrorMessage = e.message ?: e.toString(),
                    )
                }
            }
        }
    }

    fun clearUpdateError() {
        _uiState.update { it.copy(updateErrorMessage = null) }
    }

    fun clearUpdateWarning() {
        _uiState.update { it.copy(updateWarningMessage = null) }
    }
}

private data class InitialProductsPayload(
    val categories: List<Category>,
    val warehouses: List<Warehouse>,
    val optionTypes: List<ProductOptionTypeCatalog>,
    val products: List<Product>,
    val total: Int,
)

