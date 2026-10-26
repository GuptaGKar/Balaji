package com.balaji.provision.ui.catalog

import com.balaji.provision.data.network.CategoryResponse
import com.balaji.provision.data.network.ProductResponse
import com.balaji.provision.data.network.SupabaseApi
import com.balaji.provision.data.repository.CatalogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class ProductViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var mockApi: SupabaseApi

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        mockApi = mock(SupabaseApi::class.java)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadData filters inactive categories and unavailable products`() = runTest(dispatcher) {
        stubApi(
            categories = listOf(
                CategoryResponse("cat-1", "Active Cat", "active-cat", 1, true),
                CategoryResponse("cat-2", "Inactive Cat", "inactive-cat", 2, false)
            ),
            products = listOf(
                productResponse("p-1", "SKU1", "Product 1", "cat-1", active = true, available = true),
                productResponse("p-2", "SKU2", "Hidden Product", "cat-1", active = false, available = true),
                productResponse("p-3", "SKU3", "Unavailable Product", "cat-1", active = true, available = false)
            )
        )

        val viewModel = ProductViewModel(mockApi, CatalogRepository())
        advanceUntilIdle()

        assertEquals(1, viewModel.categories.value.size)
        assertEquals("cat-1", viewModel.categories.value[0].id)
        assertEquals(1, viewModel.products.value.size)
        assertEquals("p-1", viewModel.products.value[0].id)
        assertEquals(null, viewModel.error.value)
    }

    @Test
    fun `selectCategory filters products`() = runTest(dispatcher) {
        stubApi(
            categories = listOf(
                CategoryResponse("cat-1", "Cat 1", "cat-1", 1, true),
                CategoryResponse("cat-2", "Cat 2", "cat-2", 2, true)
            ),
            products = listOf(
                productResponse("p-1", "SKU1", "Product 1", "cat-1"),
                productResponse("p-2", "SKU2", "Product 2", "cat-2")
            )
        )

        val viewModel = ProductViewModel(mockApi, CatalogRepository())
        advanceUntilIdle()
        viewModel.selectCategory("cat-1")
        advanceUntilIdle()

        assertEquals("cat-1", viewModel.selectedCategory.value)
        assertEquals(1, viewModel.products.value.size)
        assertEquals("p-1", viewModel.products.value[0].id)
    }

    @Test
    fun `searchQuery filters products`() = runTest(dispatcher) {
        stubApi(
            categories = emptyList(),
            products = listOf(
                productResponse("p-1", "RICE-1", "Basmati Rice", "cat-1"),
                productResponse("p-2", "OIL-1", "Sunflower Oil", "cat-1")
            )
        )

        val viewModel = ProductViewModel(mockApi, CatalogRepository())
        advanceUntilIdle()
        viewModel.setSearchQuery("rice")
        advanceUntilIdle()

        assertEquals("rice", viewModel.searchQuery.value)
        assertEquals(1, viewModel.products.value.size)
    }

    @Test
    fun `search matches category names and slugs`() = runTest(dispatcher) {
        stubApi(
            categories = listOf(
                CategoryResponse("cat-dairy", "Dairy", "dairy", 1, true),
                CategoryResponse("cat-daal", "Daal", "dvxbxd", 2, true)
            ),
            products = listOf(
                productResponse("p-milk", "MILK-1", "Milk", "cat-dairy"),
                productResponse("p-toor", "TOOR-1", "Toor", "cat-daal")
            )
        )

        val viewModel = ProductViewModel(mockApi, CatalogRepository())
        advanceUntilIdle()

        viewModel.setSearchQuery("Dairy")
        advanceUntilIdle()
        assertEquals(listOf("p-milk"), viewModel.products.value.map { it.id })

        // The legacy slug is still a valid way to find the category.
        viewModel.setSearchQuery("dvxbxd")
        advanceUntilIdle()
        assertEquals(listOf("p-toor"), viewModel.products.value.map { it.id })
    }

    @Test
    fun `search stays inside the selected category`() = runTest(dispatcher) {
        stubApi(
            categories = listOf(
                CategoryResponse("cat-dairy", "Dairy", "dairy", 1, true),
                CategoryResponse("cat-daal", "Daal", "dvxbxd", 2, true)
            ),
            products = listOf(
                productResponse("p-milk", "MILK-1", "Milk", "cat-dairy"),
                productResponse("p-toor", "TOOR-1", "Toor", "cat-daal")
            )
        )

        val viewModel = ProductViewModel(mockApi, CatalogRepository())
        advanceUntilIdle()
        viewModel.selectCategory("cat-daal")
        viewModel.setSearchQuery("dairy")
        advanceUntilIdle()

        assertEquals(0, viewModel.products.value.size)
    }

    @Test
    fun `overlapping refreshes collapse into one request`() = runTest(dispatcher) {
        stubApi(
            categories = listOf(CategoryResponse("cat-1", "Cat 1", "cat-1", 1, true)),
            products = listOf(productResponse("p-1", "SKU1", "Product 1", "cat-1"))
        )

        val viewModel = ProductViewModel(mockApi, CatalogRepository())
        // The Categories tab refreshes on entry while the initial load may still
        // be in flight; neither call may duplicate the two network requests.
        viewModel.loadData()
        viewModel.loadData()
        advanceUntilIdle()

        verify(mockApi, times(1)).getCategories(anyString(), anyString(), anyString(), anyString())
        verify(mockApi, times(1)).getProducts(anyString(), anyString(), anyString(), anyString(), anyInt())
    }

    @Test
    fun `api failure exposes error without sample data`() = runTest(dispatcher) {
        `when`(mockApi.getCategories(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(Response.error(500, okhttp3.ResponseBody.create(null, "")))
        `when`(mockApi.getProducts(anyString(), anyString(), anyString(), anyString(), anyInt()))
            .thenReturn(Response.error(500, okhttp3.ResponseBody.create(null, "")))

        val viewModel = ProductViewModel(mockApi, CatalogRepository())
        advanceUntilIdle()

        assertNotNull(viewModel.error.value)
        assertEquals(0, viewModel.products.value.size)
        assertEquals(0, viewModel.categories.value.size)
    }

    private suspend fun stubApi(categories: List<CategoryResponse>, products: List<ProductResponse>) {
        `when`(mockApi.getCategories(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(Response.success(categories))
        `when`(mockApi.getProducts(anyString(), anyString(), anyString(), anyString(), anyInt()))
            .thenReturn(Response.success(products))
    }

    private fun productResponse(
        id: String,
        sku: String,
        name: String,
        categoryId: String,
        active: Boolean = true,
        available: Boolean = true
    ) = ProductResponse(
        id = id,
        sku = sku,
        name = name,
        category_id = categoryId,
        quantity_type = "PACKED",
        price_paise = 10000,
        price_basis_qty = 1,
        unit_label = "pack",
        step_qty = 1,
        min_qty = 1,
        stock_qty = 10,
        low_stock_threshold = 2,
        active = active,
        available = available
    )
}
