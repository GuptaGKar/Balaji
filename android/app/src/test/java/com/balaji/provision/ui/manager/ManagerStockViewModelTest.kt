package com.balaji.provision.ui.manager

import com.balaji.provision.data.repository.ManagerRepository
import com.balaji.provision.data.repository.ManagerResult
import com.balaji.provision.domain.model.LowStockProduct
import com.balaji.provision.domain.model.Product
import com.balaji.provision.domain.model.QuantityType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ManagerStockViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository: ManagerRepository = mock()
    private val product = LowStockProduct("rice", "Rice", "RICE", 10, 2, "packed", "pack")

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private suspend fun stubReads() {
        whenever(repository.storeStatus()).thenReturn(ManagerResult.Success(true))
        whenever(repository.loadOrders()).thenReturn(ManagerResult.Success(emptyList()))
        whenever(repository.loadLowStock()).thenReturn(ManagerResult.Success(emptyList()))
        whenever(repository.loadAllProducts()).thenReturn(ManagerResult.Success(emptyList()))
    }

    @Test
    fun `catalogue failure exposes retry state even when orders fail`() = runTest(dispatcher) {
        stubReads()
        whenever(repository.loadOrders()).thenReturn(ManagerResult.Failure("Orders unavailable"))
        whenever(repository.loadAllProducts()).thenReturn(ManagerResult.Failure("Check your connection"))
        val vm = ManagerViewModel(repository)
        advanceUntilIdle()
        assertEquals("Check your connection", vm.state.value.productsError)
        assertFalse(vm.state.value.isProductsLoading)
        whenever(repository.loadAllProducts()).thenReturn(ManagerResult.Success(emptyList()))
        vm.loadProducts()
        advanceUntilIdle()
        assertNull(vm.state.value.productsError)
    }

    @Test
    fun `repeated save taps submit one adjustment and report server stock`() = runTest(dispatcher) {
        stubReads()
        whenever(repository.adjustStock(any(), any(), any(), anyOrNull()))
            .thenReturn(ManagerResult.Success(15L))
        val vm = ManagerViewModel(repository)
        advanceUntilIdle()
        var confirmations = 0
        vm.adjustStock(product, 5, "Restock") { confirmations++ }
        vm.adjustStock(product, 5, "Restock") { confirmations++ }
        advanceUntilIdle()
        verify(repository, times(1)).adjustStock("rice", 5, "manual_adjustment", "Restock")
        assertEquals(1, confirmations)
        assertEquals("Rice: stock updated to 15.", vm.state.value.message)
        assertNull(vm.state.value.adjustingProductId)
    }

    @Test
    fun `failed stock update exposes error and keeps editor open`() = runTest(dispatcher) {
        stubReads()
        whenever(repository.adjustStock(any(), any(), any(), anyOrNull()))
            .thenReturn(ManagerResult.Failure("Could not reach the store"))
        val vm = ManagerViewModel(repository)
        advanceUntilIdle()
        var confirmations = 0
        vm.adjustStock(product, -2, null) { confirmations++ }
        advanceUntilIdle()
        assertEquals(0, confirmations)
        assertEquals("Could not reach the store", vm.state.value.actionError)
        assertNull(vm.state.value.message)
        assertNull(vm.state.value.adjustingProductId)
    }

    @Test
    fun `price search filters only the price list while stock search filters only the stock list`() =
        runTest(dispatcher) {
            stubReads()
            whenever(repository.loadAllProducts()).thenReturn(
                ManagerResult.Success(
                    listOf(
                        catalogueProduct("rice", "Rice", "RICE-1"),
                        catalogueProduct("oil", "Oil", "OIL-1")
                    )
                )
            )
            val vm = ManagerViewModel(repository)
            advanceUntilIdle()

            vm.setStockSearch("rice")
            assertEquals(listOf("rice"), vm.state.value.filteredStockProducts.map { it.id })
            assertEquals(listOf("rice", "oil"), vm.state.value.filteredPriceProducts.map { it.id })

            vm.setPriceSearch("oil")
            assertEquals(listOf("oil"), vm.state.value.filteredPriceProducts.map { it.id })
            assertEquals(listOf("rice"), vm.state.value.filteredStockProducts.map { it.id })

            vm.setStockSearch("")
            assertEquals(listOf("rice", "oil"), vm.state.value.filteredStockProducts.map { it.id })

            vm.setPriceSearch("")
            assertEquals(listOf("rice", "oil"), vm.state.value.filteredPriceProducts.map { it.id })
        }

    private fun catalogueProduct(id: String, name: String, sku: String) = Product(
        id = id, sku = sku, name = name, categoryId = "test",
        description = null, imagePath = null, packSizeLabel = "1 pack", maxQty = null,
        quantityType = QuantityType.PACKED, pricePaise = 12000, priceBasisQty = 1,
        unitLabel = "pack", stepQty = 1, minQty = 1, stockQty = 20,
        lowStockThreshold = 2, active = true, available = true
    )
}
