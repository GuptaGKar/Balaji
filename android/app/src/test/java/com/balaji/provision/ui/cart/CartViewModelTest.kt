package com.balaji.provision.ui.cart

import com.balaji.provision.data.network.AddressRequest
import com.balaji.provision.data.network.PlaceOrderResponse
import com.balaji.provision.data.repository.CartRepository
import com.balaji.provision.data.repository.CatalogRepository
import com.balaji.provision.data.repository.PlaceOrderResult
import com.balaji.provision.domain.model.Cart
import com.balaji.provision.domain.model.CartItem
import com.balaji.provision.domain.model.QuoteBreakdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CartViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeCartRepository
    private lateinit var viewModel: CartViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        repository = FakeCartRepository()
        viewModel = CartViewModel(repository, CatalogRepository())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `addItem delegates to repository`() = runTest(dispatcher) {
        viewModel.addItem("product-1", 2)
        advanceUntilIdle()

        assertEquals(listOf(CartItem("product-1", 2)), repository.cartFlow.value.items)
        assertEquals(null, viewModel.error.value)
    }

    @Test
    fun `updateQuantity delegates to repository`() = runTest(dispatcher) {
        repository.addItem(CartItem("product-1", 2))
        viewModel.updateQuantity("product-1", 5)
        advanceUntilIdle()

        assertEquals(listOf(CartItem("product-1", 5)), repository.cartFlow.value.items)
    }

    @Test
    fun `removing all quantity removes item`() = runTest(dispatcher) {
        repository.addItem(CartItem("product-1", 2))
        viewModel.updateQuantity("product-1", 0)
        advanceUntilIdle()

        assertTrue(repository.cartFlow.value.isEmpty)
    }

    @Test
    fun `clearCart empties repository cart`() = runTest(dispatcher) {
        repository.addItem(CartItem("product-1", 2))
        viewModel.clearCart()
        advanceUntilIdle()

        assertTrue(repository.cartFlow.value.isEmpty)
    }

    @Test
    fun `placeOrder success clears cart and exposes order number`() = runTest(dispatcher) {
        repository.addItem(CartItem("product-1", 2))
        repository.placeResult = PlaceOrderResult.Success(
            PlaceOrderResponse(public_order_number = "ORD-1")
        )

        viewModel.placeOrder(testAddress(), null)
        advanceUntilIdle()

        assertEquals(1, repository.placeCalls)
        assertTrue(repository.cartFlow.value.isEmpty)
        assertEquals(OrderPlacementState.Placed("ORD-1"), viewModel.orderPlacement.value)
    }

    @Test
    fun `placeOrder ignores a second tap while placing`() = runTest(dispatcher) {
        repository.placeResult = PlaceOrderResult.Success(
            PlaceOrderResponse(public_order_number = "ORD-1")
        )

        viewModel.placeOrder(testAddress(), null)
        viewModel.placeOrder(testAddress(), null)
        advanceUntilIdle()

        assertEquals(1, repository.placeCalls)
        assertEquals(OrderPlacementState.Placed("ORD-1"), viewModel.orderPlacement.value)
    }

    @Test
    fun `placeOrder failure keeps cart and reports message`() = runTest(dispatcher) {
        repository.addItem(CartItem("product-1", 2))
        repository.placeResult = PlaceOrderResult.Failure("Item out of stock")

        viewModel.placeOrder(testAddress(), null)
        advanceUntilIdle()

        assertEquals(listOf(CartItem("product-1", 2)), repository.cartFlow.value.items)
        assertEquals(OrderPlacementState.Failed("Item out of stock"), viewModel.orderPlacement.value)
    }

    @Test
    fun `acknowledge resets placement state`() = runTest(dispatcher) {
        repository.placeResult = PlaceOrderResult.Failure("Nope")
        viewModel.placeOrder(testAddress(), null)
        advanceUntilIdle()

        viewModel.acknowledgeOrderPlacement()

        assertEquals(OrderPlacementState.Idle, viewModel.orderPlacement.value)
    }

    private fun testAddress() = AddressRequest(
        recipient_name = "Test",
        phone = "9999999999",
        address_lines = "1 Test Street",
        postal_code = "560001"
    )

    private class FakeCartRepository : CartRepository {
        override val cartFlow = MutableStateFlow(Cart.empty())
        override val quoteFlow: StateFlow<QuoteBreakdown?> = MutableStateFlow(null)
        var placeResult: PlaceOrderResult = PlaceOrderResult.Failure("not configured")
        var placeCalls = 0

        override suspend fun getCart(): Cart = cartFlow.value

        override suspend fun addItem(item: CartItem): Cart {
            val existing = cartFlow.value.items
            val index = existing.indexOfFirst { it.productId == item.productId }
            val updated = if (index >= 0) {
                existing.toMutableList().also { it[index] = item }
            } else {
                existing + item
            }
            cartFlow.value = Cart(updated)
            return cartFlow.value
        }

        override suspend fun updateQuantity(productId: String, qtyBase: Long): Cart {
            val updated = if (qtyBase <= 0) {
                cartFlow.value.items.filterNot { it.productId == productId }
            } else {
                cartFlow.value.items.map {
                    if (it.productId == productId) CartItem(productId, qtyBase) else it
                }
            }
            cartFlow.value = Cart(updated)
            return cartFlow.value
        }

        override suspend fun removeItem(productId: String): Cart {
            cartFlow.value = Cart(cartFlow.value.items.filterNot { it.productId == productId })
            return cartFlow.value
        }

        override suspend fun clearCart() {
            cartFlow.value = Cart.empty()
        }

        override suspend fun placeOrderRemote(
            address: AddressRequest,
            customerNote: String?
        ): PlaceOrderResult {
            placeCalls++
            return placeResult
        }
    }
}
