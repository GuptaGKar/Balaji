package com.balaji.provision.ui.cart

import com.balaji.provision.data.repository.CartRepository
import com.balaji.provision.domain.model.Cart
import com.balaji.provision.domain.model.CartItem
import com.balaji.provision.domain.model.QuoteBreakdown
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

class CartViewModelTest {

    private lateinit var viewModel: CartViewModel
    private lateinit var mockRepository: CartRepository

    @Before
    fun setup() {
        mockRepository = mock(CartRepository::class.java)
        viewModel = CartViewModel(mockRepository)
    }

    @Test
    fun `addItem calls repository and updates cart`() = runBlockingTest {
        val cartItem = CartItem("product-1", 2)
        val updatedCart = Cart(items = listOf(cartItem))
        val quote = QuoteBreakdown(
            itemSubtotalPaise = 20000,
            deliveryFeePaise = 3000,
            grandTotalPaise = 23000
        )
        
        `when`(mockRepository.addItem(cartItem)).thenReturn(updatedCart)
        `when`(mockRepository.getQuote(listOf(cartItem))).thenReturn(quote)
        
        viewModel.addItem("product-1", 2)
        
        advanceUntilIdle()
        
        assertEquals(updatedCart, viewModel.cart.value)
        assertEquals(quote, viewModel.quote.value)
        verify(mockRepository).addItem(cartItem)
        verify(mockRepository).getQuote(listOf(cartItem))
    }

    @Test
    fun `updateQuantity calls repository`() = runBlockingTest {
        val updatedCart = Cart(items = listOf(CartItem("product-1", 5)))
        val quote = QuoteBreakdown(
            itemSubtotalPaise = 50000,
            deliveryFeePaise = 0,
            grandTotalPaise = 50000
        )
        
        `when`(mockRepository.updateQuantity("product-1", 5L)).thenReturn(updatedCart)
        `when`(mockRepository.getQuote(listOf(CartItem("product-1", 5)))).thenReturn(quote)
        
        viewModel.updateQuantity("product-1", 5)
        
        advanceUntilIdle()
        
        assertEquals(updatedCart, viewModel.cart.value)
        verify(mockRepository).updateQuantity("product-1", 5L)
    }

    @Test
    fun `removeItem calls repository`() = runBlockingTest {
        val emptyCart = Cart.empty()
        
        `when`(mockRepository.removeItem("product-1")).thenReturn(emptyCart)
        
        viewModel.removeItem("product-1")
        
        advanceUntilIdle()
        
        assertEquals(emptyCart, viewModel.cart.value)
        verify(mockRepository).removeItem("product-1")
    }

    @Test
    fun `clearCart calls repository`() = runBlockingTest {
        viewModel.clearCart()
        
        advanceUntilIdle()
        
        assertEquals(Cart.empty(), viewModel.cart.value)
        verify(mockRepository).clearCart()
    }
}