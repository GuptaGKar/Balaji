package com.balaji.provision.ui.cart

import com.balaji.provision.domain.model.Cart
import com.balaji.provision.domain.model.CartItem
import com.balaji.provision.domain.model.QuantityType
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class CartLogicTest {

    @Test
    fun `cart item count sums qtyBase`() {
        val cart = Cart(
            items = listOf(
                CartItem("product-1", 2),
                CartItem("product-2", 3),
                CartItem("product-3", 1)
            )
        )
        assertEquals(6, cart.itemCount)
    }

    @Test
    fun `cart is empty when no items`() {
        val cart = Cart.empty()
        assertTrue(cart.isEmpty)
        assertEquals(0, cart.itemCount)
    }

    @Test
    fun `cart item equality includes quantity so StateFlow emits updates`() {
        val item1 = CartItem("product-1", 2)
        val item2 = CartItem("product-1", 5)
        val item3 = CartItem("product-2", 2)

        assertNotEquals(item1, item2)
        assertNotEquals(Cart(listOf(item1)), Cart(listOf(item2)))
        assertNotEquals(item1, item3)
        assertEquals(CartItem("product-1", 2), CartItem("product-1", 2))
    }
}