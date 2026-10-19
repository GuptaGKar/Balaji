package com.balaji.provision.ui.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.balaji.provision.BuildConfig
import com.balaji.provision.data.auth.AuthRepository
import com.balaji.provision.data.network.SupabaseApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val api: SupabaseApi,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _orders = MutableStateFlow<List<OrderView>>(emptyList())
    val orders: StateFlow<List<OrderView>> = _orders.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.session.collect { session ->
                if (session == null) {
                    _orders.value = emptyList()
                    _error.value = "Sign in to see your orders."
                } else {
                    fetchOrders()
                }
            }
        }
    }

    fun fetchOrders() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val authToken = authRepository.authorizedToken()
            if (authToken == null) {
                _isLoading.value = false
                _orders.value = emptyList()
                _error.value = "Sign in to see your orders."
                return@launch
            }
            try {
                val anonKey = BuildConfig.SUPABASE_ANON_KEY
                val response = api.getOrders(apiKey = anonKey, auth = "Bearer $authToken")
                if (response.isSuccessful && response.body() != null) {
                    val mapped = response.body()!!.map { r ->
                        OrderView(
                            id = r.id,
                            publicOrderNumber = r.public_order_number,
                            status = r.status,
                            grandTotalPaise = r.grand_total_paise,
                            createdAt = formatDate(r.created_at),
                            items = r.order_items?.map { item ->
                                OrderItemView(
                                    name = item.name_snapshot,
                                    qty = item.qty_base,
                                    unitPricePaise = item.line_total_paise
                                )
                            } ?: emptyList()
                        )
                    }
                    _orders.value = mapped
                } else {
                    _orders.value = emptyList()
                    _error.value = "Could not load orders. Please try again."
                }
            } catch (e: Exception) {
                _orders.value = emptyList()
                _error.value = "Could not load orders. Check your connection and try again."
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun formatDate(dateStr: String): String {
        return if (dateStr.length >= 10) dateStr.substring(0, 10) else dateStr
    }
}
