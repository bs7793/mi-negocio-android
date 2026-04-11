package superapps.minegocio.ui.productsscreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import superapps.minegocio.ui.categoriesscreen.Category
import superapps.minegocio.ui.warehousesscreen.Warehouse

data class ProductsUiState(
    val isLoading: Boolean = true,
    val products: List<Product> = emptyList(),
    val categories: List<Category> = emptyList(),
    val warehouses: List<Warehouse> = emptyList(),
    val total: Int = 0,
    val searchQuery: String = "",
    val selectedCategoryId: Long? = null,
    val selectedWarehouseId: Long? = null,
    val errorMessage: String? = null,
    val isCreatingProduct: Boolean = false,
    val createErrorMessage: String? = null,
)

class ProductsViewModel(
    private val repository: ProductsRepository = ProductsRepository(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProductsUiState())
    val uiState: StateFlow<ProductsUiState> = _uiState.asStateFlow()

    init {
        loadInitial()
    }

    fun loadInitial() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val categoriesDeferred = async { repository.fetchCategories() }
                val warehousesDeferred = async { repository.fetchWarehouses() }
                val productsDeferred = async {
                    repository.fetchProducts(
                        search = _uiState.value.searchQuery,
                        categoryId = _uiState.value.selectedCategoryId,
                        warehouseId = _uiState.value.selectedWarehouseId,
                    )
                }
                val categories = categoriesDeferred.await()
                val warehouses = warehousesDeferred.await()
                val products = productsDeferred.await()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        categories = categories,
                        warehouses = warehouses,
                        products = products.items,
                        total = products.total,
                        errorMessage = null,
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

    fun createProduct(payload: CreateProductPayload) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingProduct = true, createErrorMessage = null) }
            try {
                repository.createProduct(payload)
                val refreshed = repository.fetchProducts(
                    search = _uiState.value.searchQuery,
                    categoryId = _uiState.value.selectedCategoryId,
                    warehouseId = _uiState.value.selectedWarehouseId,
                )
                _uiState.update {
                    it.copy(
                        isCreatingProduct = false,
                        createErrorMessage = null,
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
}

