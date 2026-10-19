package com.balaji.provision.ui.catalog

import com.balaji.provision.data.network.CategoryResponse
import com.balaji.provision.data.network.ProductResponse
import com.balaji.provision.data.network.SupabaseApi
import com.balaji.provision.data.repository.CatalogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class ProductViewModelTest {

    private lateinit var viewModel: ProductViewModel
    private lateinit var mockApi: SupabaseApi
    private lateinit var mockCatalogRepo: CatalogRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockApi = mock(SupabaseApi::class.java)
        mockCatalogRepo = CatalogRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadData filters active categories and products`() = runTest(testDispatcher) {
        val categories = listOf(
            CategoryResponse("cat-1", "Active Cat", "active-cat", 1, true),
            CategoryResponse("cat-2", "Inactive Cat", "inactive-cat", 2, false)
        )
        val products = listOf(
            ProductResponse("p-1", "SKU1", "Product 1", null, "cat-1", null, "PACKED", 10000, 1, "pack", null, 1, 1, null, 10, 0, active = true, available = true)
        )
        
        `when`(mockApi.getCategories(anyString(), anyString(), anyString(), anyString())).thenReturn(retrofit2.Response.success(categories))
        `when`(mockApi.getProducts(anyString(), anyString(), anyString(), anyString(), anyInt())).thenReturn(retrofit2.Response.success(products))
        
        viewModel = ProductViewModel(mockApi, mockCatalogRepo)
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertEquals(1, viewModel.categories.value.size)
        assertEquals("cat-1", viewModel.categories.value[0].id)
    }

    @Test
    fun `searchQuery updates`() = runTest(testDispatcher) {
        viewModel = ProductViewModel(mockApi, mockCatalogRepo)
        viewModel.setSearchQuery("test query")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("test query", viewModel.searchQuery.value)
    }
}