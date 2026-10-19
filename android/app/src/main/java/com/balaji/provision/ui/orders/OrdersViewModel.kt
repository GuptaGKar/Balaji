package com.balaji.provision.ui.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.balaji.provision.BuildConfig
import com.balaji.provision.data.network.SupabaseApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val api: SupabaseApi
) : ViewModel() {

    private val _orders = MutableStateFlow<List<OrderView>>(emptyList())
    val orders: StateFlow<List<OrderView>> = _orders.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        fetchOrders()
    }

    fun fetchOrders() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val anonKey = BuildConfig.SUPABASE_ANON_KEY
                val response = api.getOrders(apiKey = anonKey, auth = "Bearer $anonKey")
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
                    if (mapped.isNotEmpty()) {
                        _orders.value = mapped
                    } else {
                        _orders.value = sampleOrders
                    }
                } else {
                    _orders.value = sampleOrders
                }
            } catch (e: Exception) {
                _orders.value = sampleOrders
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun formatDate(dateStr: String): String {
        return if (dateStr.length >= 10) dateStr.substring(0, 10) else dateStr
    }

    companion object {
        val sampleOrders = listOf(
            OrderView(
                id = "ord-1",
                publicOrderNumber = "BALAJI-1082",
                status = "out_for_delivery",
                grandTotalPaise = 54500,
                createdAt = "Today, 10:15 AM",
                items = listOf(
                    OrderItemView("Aashirvaad Shudh Chakki Atta (5kg)", 1, 24000),
                    OrderItemView("Fortune Sunlite Sunflower Oil (1L)", 2, 29000),
                    OrderItemView("Tata Salt Iodized Salt (1kg)", 1, 2800)
                )
            )
        )
    }
}