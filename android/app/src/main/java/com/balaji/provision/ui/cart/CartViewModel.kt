package com.balaji.provision.ui.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.balaji.provision.data.network.AddressRequest
import com.balaji.provision.data.repository.CartRepository
import com.balaji.provision.data.repository.CatalogRepository
import com.balaji.provision.data.repository.PlaceOrderResult
import com.balaji.provision.domain.model.Cart
import com.balaji.provision.domain.model.CartItem
import com.balaji.provision.domain.model.Product
import com.balaji.provision.domain.model.QuoteBreakdown
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface OrderPlacementState {
    data object Idle : OrderPlacementState
    data object Placing : OrderPlacementState
    /** [orderId] lets the checkout screen attach the optional order photo after placement. */
    data class Placed(val orderNumber: String, val orderId: String = "") : OrderPlacementState
    data class Failed(val message: String) : OrderPlacementState
}

@HiltViewModel
class CartViewModel @Inject constructor(
    private val cartRepository: CartRepository,
    private val catalogRepository: CatalogRepository
) : ViewModel() {
    
    val cart: StateFlow<Cart> = cartRepository.cartFlow
    val quote: StateFlow<QuoteBreakdown?> = cartRepository.quoteFlow

    private val _orderPlacement = MutableStateFlow<OrderPlacementState>(OrderPlacementState.Idle)
    val orderPlacement: StateFlow<OrderPlacementState> = _orderPlacement.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    /** The raw text the user typed in the coupon field (before Apply). */
    private val _couponInput = MutableStateFlow("")
    val couponInput: StateFlow<String> = _couponInput.asStateFlow()
    
    fun addItem(productId: String, qtyBase: Long = 1) {
        viewModelScope.launch {
            try {
                cartRepository.addItem(CartItem(productId, qtyBase))
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }
    
    fun updateQuantity(productId: String, qtyBase: Long) {
        viewModelScope.launch {
            try {
                if (qtyBase <= 0) {
                    cartRepository.removeItem(productId)
                } else {
                    cartRepository.updateQuantity(productId, qtyBase)
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }
    
    fun removeItem(productId: String) {
        viewModelScope.launch {
            try {
                cartRepository.removeItem(productId)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }
    
    fun refreshQuote() {
        cartRepository.refreshQuote()
    }

    /** Update the typed coupon code without applying it yet. */
    fun onCouponInputChange(value: String) {
        _couponInput.value = value
    }

    /** Apply the current coupon input and trigger a re-quote. */
    fun applyCoupon() {
        val code = _couponInput.value.trim()
        if (code.isNotEmpty()) {
            cartRepository.applyCoupon(code)
        }
    }

    /** Remove any applied coupon and trigger a re-quote. */
    fun removeCoupon() {
        _couponInput.value = ""
        cartRepository.removeCoupon()
    }

    fun findProduct(productId: String): Product? = catalogRepository.findProductById(productId)

    /**
     * Places the order from the ViewModel so the request survives rotation and
     * tab changes. The cart is cleared as soon as the store confirms it, which
     * makes a second submission of the same basket impossible.
     */
    fun placeOrder(address: AddressRequest, customerNote: String?) {
        if (_orderPlacement.value is OrderPlacementState.Placing) return
        _orderPlacement.value = OrderPlacementState.Placing
        viewModelScope.launch {
            val result = try {
                cartRepository.placeOrderRemote(address, customerNote)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PlaceOrderResult.Failure(e.message ?: "Could not place the order. Please try again.")
            }
            when (result) {
                is PlaceOrderResult.Success -> {
                    try {
                        cartRepository.clearCart()
                    } catch (_: Exception) {
                        // The order exists; an empty local basket is best-effort.
                    }
                    _orderPlacement.value = OrderPlacementState.Placed(
                        result.response.public_order_number.orEmpty(),
                        result.response.order_id.orEmpty()
                    )
                }
                is PlaceOrderResult.Failure -> {
                    _orderPlacement.value = OrderPlacementState.Failed(result.message)
                }
            }
        }
    }

    fun acknowledgeOrderPlacement() {
        if (_orderPlacement.value !is OrderPlacementState.Placing) {
            _orderPlacement.value = OrderPlacementState.Idle
        }
    }
    
    fun clearError() {
        _error.value = null
    }
    
    fun clearCart() {
        viewModelScope.launch {
            cartRepository.clearCart()
        }
        removeCoupon()
    }
}