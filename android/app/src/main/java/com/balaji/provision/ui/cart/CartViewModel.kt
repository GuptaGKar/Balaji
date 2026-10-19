package com.balaji.provision.ui.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.balaji.provision.data.network.AddressRequest
import com.balaji.provision.data.network.PlaceOrderResponse
import com.balaji.provision.data.repository.CartRepository
import com.balaji.provision.domain.model.Cart
import com.balaji.provision.domain.model.CartItem
import com.balaji.provision.domain.model.QuoteBreakdown
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CartViewModel @Inject constructor(
    private val cartRepository: CartRepository
) : ViewModel() {
    
    val cart: StateFlow<Cart> = cartRepository.cartFlow
    val quote: StateFlow<QuoteBreakdown?> = cartRepository.quoteFlow
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    
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
    
    suspend fun placeOrderRemote(address: AddressRequest, customerNote: String?): PlaceOrderResponse? {
        return cartRepository.placeOrderRemote(address, customerNote)
    }
    
    fun clearError() {
        _error.value = null
    }
    
    fun clearCart() {
        viewModelScope.launch {
            cartRepository.clearCart()
        }
    }
}