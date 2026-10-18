package com.balaji.provision.ui.catalog

import com.balaji.provision.data.network.SupabaseApi
import com.balaji.provision.domain.model.Category
import com.balaji.provision.domain.model.Product
import com.balaji.provision.domain.model.QuantityType
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

class ProductViewModelTest {

    private lateinit var viewModel: ProductViewModel
    private lateinit var mockApi: SupabaseApi

    @Before
    fun setup() {
        mockApi = mock(SupabaseApi::class.java)
        viewModel = ProductViewModel(mockApi)
    }

    @Test
    fun `loadCategories filters active`() = runBlockingTest {
        val categories = listOf(
            Category("cat-1", "Active Cat", "active-cat", 1, true),
            Category("cat-2", "Inactive Cat", "inactive-cat", 2, false)
        )
        
        val response = retrofit2.Response.success(categories)
        `when`(mockApi.getCategories(
            anyString(),
            anyString(),
            anyString(),
            anyString()
        )).thenReturn(response)
        
        viewModel.loadCategories()
        
        advanceUntilIdle()
        
        assertEquals(1, viewModel.categories.value.size)
        assertEquals("cat-1", viewModel.categories.value[0].id)
    }

    @Test
    fun `selectCategory filters products`() = runBlockingTest {
        val products = listOf(
            Product("p-1", "SKU1", "Product 1", null, "cat-1", null, QuantityType.PACKED, 10000, 1, "pack", null, 1, 1, null, 10, 0, true, true),
            Product("p-2", "SKU2", "Product 2", null, "cat-2", null, QuantityType.PACKED, 20000, 1, "pack", null, 1, 1, null, 10, 0, true, true)
        )
        
        val response = retrofit2.Response.success(products)
        `when`(mockApi.getProducts(
            anyString(),
            anyString(),
            anyString(),
            anyInt()
        )).thenReturn(response)
        
        viewModel.selectCategory("cat-1")
        
        advanceUntilIdle()
        
        assertEquals("cat-1", viewModel.selectedCategory.value)
    }

    @Test
    fun `searchQuery updates`() = runBlockingTest {
        viewModel.setSearchQuery("test query")
        assertEquals("test query", viewModel.searchQuery.value)
    }
}