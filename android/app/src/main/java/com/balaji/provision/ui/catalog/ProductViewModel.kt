package com.balaji.provision.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.balaji.provision.BuildConfig
import com.balaji.provision.data.network.CategoryResponse
import com.balaji.provision.data.network.SupabaseApi
import com.balaji.provision.data.network.toDomain
import com.balaji.provision.data.repository.CatalogRepository
import com.balaji.provision.domain.model.Category
import com.balaji.provision.domain.model.Product
import com.balaji.provision.domain.model.ProductVariantGroup
import com.balaji.provision.domain.model.toVariantGroups
import com.balaji.provision.domain.model.variantsMatch
import com.balaji.provision.data.network.ProductResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.Response
import javax.inject.Inject

@HiltViewModel
class ProductViewModel @Inject constructor(
    private val api: SupabaseApi,
    private val catalogRepository: CatalogRepository
) : ViewModel() {
    
    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories = _categories.asStateFlow()
    
    private val _allProducts = MutableStateFlow<List<Product>>(emptyList())

    private val _variantGroups = MutableStateFlow<List<ProductVariantGroup>>(emptyList())
    val variantGroups = _variantGroups.asStateFlow()

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

    private var loadJob: Job? = null

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
        // A tab switch and an explicit retry can arrive together. Keep a single
        // in-flight fetch instead of duplicating the two catalogue requests.
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            _isLoading.value = _allProducts.value.isEmpty()
            _error.value = null
            try {
                val anonKey = BuildConfig.SUPABASE_ANON_KEY
                val (catResponse, prodResponse) = coroutineScope {
                    val categories = async {
                        fetchCategories(anonKey)
                    }
                    val products = async {
                        fetchProducts(anonKey)
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

    /**
     * Fetches categories, falling back to the pre-image column list when the
     * live database has not had `image_path` added yet, so a pending migration
     * never blocks the customer catalogue.
     */
    private suspend fun fetchCategories(anonKey: String): Response<List<CategoryResponse>> {
        val withImage = api.getCategories(
            apiKey = anonKey,
            auth = "Bearer $anonKey"
        )
        if (withImage.isSuccessful || withImage.code() != 400) return withImage
        return api.getCategories(
            apiKey = anonKey,
            auth = "Bearer $anonKey",
            select = LEGACY_CATEGORY_SELECT
        )
    }

    private suspend fun fetchProducts(anonKey: String): Response<List<ProductResponse>> {
        val withVariants = api.getProducts(
            apiKey = anonKey,
            auth = "Bearer $anonKey"
        )
        if (withVariants.isSuccessful || withVariants.code() != 400) return withVariants
        return api.getProducts(
            apiKey = anonKey,
            auth = "Bearer $anonKey",
            select = LEGACY_PRODUCT_SELECT
        )
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
            // A query may name a category rather than a product, e.g. "Dairy", or use
            // a common Hindi/Punjabi word ("doodh", "atta"). Expand it to a few related
            // terms, then rank: a name match beats a category match, which beats a
            // match buried in the description ("milk" must not lead with a chocolate
            // bar whose blurb says "milky caramel").
            val terms = searchTerms(query)
            val matchingCategoryIds = _categories.value
                .filter { category ->
                    terms.any { term ->
                        category.name.lowercase().contains(term) ||
                            category.slug.lowercase().contains(term)
                    }
                }
                .mapTo(mutableSetOf()) { it.id }

            val scored = filtered.mapNotNull { product ->
                val score = relevance(product, terms, matchingCategoryIds)
                if (score > 0) product to score else null
            }
            val best = scored.maxOfOrNull { it.second } ?: 0
            // When real name matches exist, drop weak description-only hits entirely.
            filtered = scored
                .filter { best < NAME_MATCH_SCORE || it.second >= CATEGORY_MATCH_SCORE }
                .sortedByDescending { it.second } // stable: catalogue order within a tie
                .map { it.first }
        }

        _products.value = filtered
        _variantGroups.value = filtered.toVariantGroups()
    }

    private fun searchTerms(query: String): Set<String> =
        buildSet {
            add(query)
            SEARCH_ALIASES[query]?.let { addAll(it) }
        }

    private fun relevance(product: Product, terms: Set<String>, categoryIds: Set<String>): Int {
        val name = product.name.lowercase()
        val nameWords = name.split(' ', '-', '|', '(', ')', ',', '/')
        var best = 0
        for (term in terms) {
            val nameScore = when {
                name.startsWith(term) -> 100
                nameWords.any { it.startsWith(term) } -> 80
                name.contains(term) -> NAME_MATCH_SCORE
                else -> 0
            }
            best = maxOf(best, nameScore)
            if (nameScore == 0) {
                if (product.packLabel.lowercase().contains(term) ||
                    product.sku.lowercase().contains(term) ||
                    product.variantsMatch(term)
                ) {
                    best = maxOf(best, 20)
                }
                if (product.description?.lowercase()?.contains(term) == true) {
                    best = maxOf(best, 10)
                }
            }
        }
        if (product.categoryId in categoryIds) best = maxOf(best, CATEGORY_MATCH_SCORE)
        return best
    }

    /** Finds a variant group by the id of any product in the group. */
    fun findVariantGroup(productId: String): ProductVariantGroup? =
        _variantGroups.value.firstOrNull { group ->
            group.groupId == productId || group.variants.any { it.id == productId }
        } ?: catalogRepository.liveProducts.toVariantGroups().firstOrNull { group ->
            group.groupId == productId || group.variants.any { it.id == productId }
        }

    fun clearError() {
        _error.value = null
    }

    private companion object {
        const val NAME_MATCH_SCORE = 60
        const val CATEGORY_MATCH_SCORE = 40

        /**
         * Starter list of everyday words customers type that are not in product
         * names. Values are matched against product names and category names, so
         * keep them to words that appear there. Extend it from real "no items
         * found" searches and product requests.
         */
        val SEARCH_ALIASES: Map<String, Set<String>> = mapOf(
            "milk" to setOf("dairy"), "doodh" to setOf("milk", "dairy"),
            "dahi" to setOf("curd", "dairy"), "curd" to setOf("dahi", "dairy"),
            "paneer" to setOf("dairy"), "butter" to setOf("dairy"), "cheese" to setOf("dairy"),
            "lassi" to setOf("dairy"),
            "atta" to setOf("flour"), "aata" to setOf("atta", "flour"),
            "maida" to setOf("flour"), "suji" to setOf("flour"), "besan" to setOf("flour"),
            "dal" to setOf("pulses"), "daal" to setOf("dal", "pulses"),
            "chana" to setOf("pulses"), "rajma" to setOf("pulses"),
            "tel" to setOf("oil"), "oil" to setOf("ghee"),
            "masala" to setOf("spices"), "mirch" to setOf("spices", "chilli"),
            "haldi" to setOf("spices", "turmeric"),
            "chai" to setOf("tea", "drinks"), "juice" to setOf("drinks"),
            "cola" to setOf("drinks"), "coffee" to setOf("drinks"),
            "chips" to setOf("snacks"), "namkeen" to setOf("snacks"),
            "bhujia" to setOf("namkeen", "snacks"),
            "achar" to setOf("pickle"), "pickle" to setOf("achar"),
            "sabun" to setOf("soap", "personal care"), "soap" to setOf("personal care"),
            "shampoo" to setOf("personal care"), "toothpaste" to setOf("personal care"),
            "surf" to setOf("detergent", "household"), "detergent" to setOf("household"),
            "ketchup" to setOf("sauce", "sauces")
        )

        /** Column list used before the category-images migration is applied. */
        const val LEGACY_CATEGORY_SELECT = "id,name,slug,sort_order,active"

        /** Column list used before the variant-grouping migration is applied. */
        const val LEGACY_PRODUCT_SELECT =
            "id,sku,name,description,category_id,image_path,quantity_type,price_paise,mrp_paise," +
                "price_basis_qty,unit_label,pack_size_label,step_qty,min_qty,max_qty," +
                "stock_qty,low_stock_threshold,active,available"
    }
}