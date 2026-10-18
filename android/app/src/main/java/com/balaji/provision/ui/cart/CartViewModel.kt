package com.balaji.provision.ui.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.balaji.provision.data.repository.CartRepository
import com.balaji.provision.domain.model.Cart
import com.balaji.provision.domain.model.CartItem
import com.balaji.provision.domain.model.QuoteBreakdown
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CartViewModel @Inject constructor(
    private val cartRepository: CartRepository
) : ViewModel() {
    
    private val _cart = MutableStateFlow<Cart>(Cart.empty())
    val cart = _cart.asStateFlow()
    
    private val _quote = MutableStateFlow<QuoteBreakdown?>(null)
    val quote = _quote.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _quote.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    
    init {
        loadCart()
    }
    
    fun loadCart() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val cart = cartRepository.getCart()
                _cart.value = cart
                if (cart.items.isNotEmpty()) {
                    val quote = cartRepository.getQuote(cart.items)
                    _quote.value = quote
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun addItem(productId: String, qtyBase: Long = 1) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val item = CartItem(productId, qtyBase)
                val updatedCart = cartRepository.addItem(item)
                _cart.value = updatedCart
                val quote = cartRepository.getQuote(updatedCart.items)
                _quote.value = quote
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }
    
    fun updateQuantity(productId: String, qtyBase: Long) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val updatedCart = cartRepository.updateQuantity(productId, qtyBase)
                _cart.value = updatedCart
                if (updatedCart.items.isNotEmpty()) {
                    val quote = cartRepository.getQuote(updatedCart.items)
                    _quote.value = quote
                } else {
                    _quote.value = null
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }
    
    fun removeItem(productId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val updatedCart = cartRepository.removeItem(productId)
                _cart.value = updatedCart
                if (updatedCart.items.isNotEmpty()) {
                    val quote = cartRepository.getQuote(updatedCart.items)
                    _quote.value = quote
                } else {
                    _quote.value = null
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }
    
    fun clearError() {
        _error.value = null
    }
    
    fun clearCart() {
        viewModelScope.launch {
            cartRepository.clearCart()
            _cart.value = Cart.empty()
            _quote.value = null
        }
    }
}