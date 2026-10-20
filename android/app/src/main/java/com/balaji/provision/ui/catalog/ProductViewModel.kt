package com.balaji.provision.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.balaji.provision.BuildConfig
import com.balaji.provision.data.network.SupabaseApi
import com.balaji.provision.data.network.toDomain
import com.balaji.provision.data.repository.CatalogRepository
import com.balaji.provision.domain.model.Category
import com.balaji.provision.domain.model.Product
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProductViewModel @Inject constructor(
    private val api: SupabaseApi,
    private val catalogRepository: CatalogRepository
) : ViewModel() {
    
    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories = _categories.asStateFlow()
    
    private val _allProducts = MutableStateFlow<List<Product>>(emptyList())
    
    private val _products = MutableStateFlow<List<Product>>(emptyList())
    val products = _products.asStateFlow()
    
    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory = _selectedCategory.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    
    init {
        // Show the last live catalogue immediately when returning to the tab,
        // then refresh in the background.
        if (catalogRepository.liveCategories.isNotEmpty()) {
            _categories.value = catalogRepository.liveCategories
            _allProducts.value = catalogRepository.liveProducts
            filterProducts()
        }
        loadData()
    }
    
    fun loadData() {
        viewModelScope.launch {
            _isLoading.value = _allProducts.value.isEmpty()
            _error.value = null
            try {
                val (catResponse, prodResponse) = coroutineScope {
                    val categories = async {
                        api.getCategories(
                            apiKey = BuildConfig.SUPABASE_ANON_KEY,
                            auth = "Bearer ${BuildConfig.SUPABASE_ANON_KEY}"
                        )
                    }
                    val products = async {
                        api.getProducts(
                            apiKey = BuildConfig.SUPABASE_ANON_KEY,
                            auth = "Bearer ${BuildConfig.SUPABASE_ANON_KEY}"
                        )
                    }
                    categories.await() to products.await()
                }
                
                if (catResponse.isSuccessful && prodResponse.isSuccessful) {
                    val catList = catResponse.body()?.filter { it.active }?.map { it.toDomain() } ?: emptyList()
                    val prodList = prodResponse.body()?.filter { it.active && it.available }?.map { it.toDomain() } ?: emptyList()

                    catalogRepository.liveCategories = catList
                    catalogRepository.liveProducts = prodList
                    _categories.value = catList
                    _allProducts.value = prodList
                    if (_selectedCategory.value !in catList.map { it.id }) {
                        _selectedCategory.value = null
                    }
                } else {
                    _error.value = "Could not refresh the catalogue. Pull to retry or check your connection."
                }
            } catch (e: Exception) {
                _error.value = "Could not refresh the catalogue. Check your connection and try again."
            } finally {
                filterProducts()
                _isLoading.value = false
            }
        }
    }
    
    fun selectCategory(categoryId: String?) {
        _selectedCategory.value = categoryId
        filterProducts()
    }
    
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        filterProducts()
    }
    
    private fun filterProducts() {
        val catId = _selectedCategory.value
        val query = _searchQuery.value.trim().lowercase()

        var filtered = _allProducts.value

        if (!catId.isNullOrBlank()) {
            filtered = filtered.filter { it.categoryId == catId }
        }

        if (query.isNotEmpty()) {
            filtered = filtered.filter {
                it.name.lowercase().contains(query) ||
                    (it.description?.lowercase()?.contains(query) == true) ||
                    it.sku.lowercase().contains(query)
            }
        }

        _products.value = filtered
    }
    
    fun clearError() {
        _error.value = null
    }
}