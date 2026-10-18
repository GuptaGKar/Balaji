package com.balaji.provision.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.balaji.provision.data.network.SupabaseApi
import com.balaji.provision.domain.model.Category
import com.balaji.provision.domain.model.Product
import com.balaji.provision.domain.model.QuantityType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProductViewModel @Inject constructor(
    private val api: SupabaseApi
) : ViewModel() {
    
    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories = _categories.asStateFlow()
    
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
        loadCategories()
    }
    
    fun loadCategories() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = api.getCategories(
                    apiKey = BuildConfig.SUPABASE_ANON_KEY,
                    auth = "Bearer ${BuildConfig.SUPABASE_ANON_KEY}"
                )
                if (response.isSuccessful) {
                    _categories.value = response.body()!!.filter { it.active }
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun loadProducts(categoryId: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // In a real implementation, you'd filter by category
                val response = api.getProducts(
                    apiKey = BuildConfig.SUPABASE_ANON_KEY,
                    auth = "Bearer ${BuildConfig.SUPABASE_ANON_KEY}"
                )
                if (response.isSuccessful) {
                    var products = response.body()!!
                    if (categoryId != null) {
                        products = products.filter { it.category_id == categoryId }
                    }
                    _products.value = products.filter { it.active && it.available }
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun selectCategory(categoryId: String?) {
        _selectedCategory.value = categoryId
        loadProducts(categoryId)
    }
    
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        // Filter products locally
        // In a real app, you'd debounce and call API
    }
    
    fun clearError() {
        _error.value = null
    }
}