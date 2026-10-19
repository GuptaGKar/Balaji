package com.balaji.provision.ui.cart

import com.balaji.provision.data.repository.CartRepository
import com.balaji.provision.domain.model.Cart
import com.balaji.provision.domain.model.CartItem
import com.balaji.provision.domain.model.QuoteBreakdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class CartViewModelTest {

    private lateinit var viewModel: CartViewModel
    private lateinit var mockRepository: CartRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockRepository = mock(CartRepository::class.java)
        
        `when`(mockRepository.cartFlow).thenReturn(MutableStateFlow(Cart.empty()))
        `when`(mockRepository.quoteFlow).thenReturn(MutableStateFlow(null))
        
        viewModel = CartViewModel(mockRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `addItem calls repository`() = runTest(testDispatcher) {
        val cartItem = CartItem("product-1", 2)
        val updatedCart = Cart(items = listOf(cartItem))
        
        `when`(mockRepository.addItem(cartItem)).thenReturn(updatedCart)
        
        viewModel.addItem("product-1", 2)
        testDispatcher.scheduler.advanceUntilIdle()
        
        verify(mockRepository).addItem(cartItem)
    }

    @Test
    fun `updateQuantity calls repository`() = runTest(testDispatcher) {
        val updatedCart = Cart(items = listOf(CartItem("product-1", 5)))
        
        `when`(mockRepository.updateQuantity("product-1", 5L)).thenReturn(updatedCart)
        
        viewModel.updateQuantity("product-1", 5)
        testDispatcher.scheduler.advanceUntilIdle()
        
        verify(mockRepository).updateQuantity("product-1", 5L)
    }

    @Test
    fun `removeItem calls repository`() = runTest(testDispatcher) {
        val emptyCart = Cart.empty()
        
        `when`(mockRepository.removeItem("product-1")).thenReturn(emptyCart)
        
        viewModel.removeItem("product-1")
        testDispatcher.scheduler.advanceUntilIdle()
        
        verify(mockRepository).removeItem("product-1")
    }

    @Test
    fun `clearCart calls repository`() = runTest(testDispatcher) {
        viewModel.clearCart()
        testDispatcher.scheduler.advanceUntilIdle()
        
        verify(mockRepository).clearCart()
    }
}