package com.balaji.provision.ui.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.balaji.provision.BuildConfig
import com.balaji.provision.data.auth.AuthRepository
import com.balaji.provision.data.network.CancelOrderRequest
import com.balaji.provision.data.network.SupabaseApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.Response
import javax.inject.Inject

sealed interface CancelOrderResult {
    data object Success : CancelOrderResult
    data class Failure(val message: String) : CancelOrderResult
}

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

    private val _isCancelling = MutableStateFlow(false)
    val isCancelling: StateFlow<Boolean> = _isCancelling.asStateFlow()

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
            val session = authRepository.session.value
            if (session == null) {
                _isLoading.value = false
                _orders.value = emptyList()
                _error.value = "Sign in to see your orders."
                return@launch
            }
            val authToken = authRepository.authorizedToken()
            if (authToken == null) {
                _isLoading.value = false
                _orders.value = emptyList()
                _error.value = "Your session expired. Sign in again to see your orders."
                return@launch
            }
            try {
                val anonKey = BuildConfig.SUPABASE_ANON_KEY
                val response = api.getOrders(
                    apiKey = anonKey,
                    auth = "Bearer $authToken",
                    customerId = "eq.${session.userId}"
                )
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    _orders.value = body.map { order ->
                        OrderView(
                            id = order.id,
                            publicOrderNumber = order.public_order_number,
                            status = order.status,
                            grandTotalPaise = order.grand_total_paise,
                            createdAt = formatDate(order.created_at),
                            items = order.order_items.orEmpty().map { item ->
                                OrderItemView(
                                    id = item.name_snapshot + item.qty_base + item.line_total_paise,
                                    name = item.name_snapshot,
                                    qty = item.qty_base,
                                    lineTotalPaise = item.line_total_paise,
                                    quantityType = item.quantity_type_snapshot,
                                    unitLabel = item.unit_label_snapshot,
                                    removed = !item.removed_at.isNullOrBlank(),
                                    removedReason = item.removed_reason
                                )
                            }
                        )
                    }
                } else {
                    _orders.value = emptyList()
                    _error.value = response.apiErrorMessage("Could not load orders. Please try again.")
                }
            } catch (e: Exception) {
                _orders.value = emptyList()
                _error.value = "Could not load orders. Check your connection and try again."
            } finally {
                _isLoading.value = false
            }
        }
    }

    suspend fun cancelOrder(orderId: String, reason: String?): CancelOrderResult {
        val authToken = authRepository.authorizedToken()
            ?: return CancelOrderResult.Failure("Sign in again to cancel this order.")
        _isCancelling.value = true
        return try {
            val response = api.cancelMyOrder(
                apiKey = BuildConfig.SUPABASE_ANON_KEY,
                auth = "Bearer $authToken",
                body = CancelOrderRequest(
                    p_order_id = orderId,
                    p_reason = reason?.trim()?.ifBlank { null }
                )
            )
            val body = response.body()
            when {
                !response.isSuccessful -> CancelOrderResult.Failure(
                    response.apiErrorMessage("Could not cancel the order. Please try again.")
                )
                body == null -> CancelOrderResult.Failure("Could not cancel the order. Please try again.")
                body.error != null -> CancelOrderResult.Failure(body.message ?: body.error)
                body.success == true -> {
                    fetchOrders()
                    CancelOrderResult.Success
                }
                else -> CancelOrderResult.Failure(body.message ?: "Could not cancel the order.")
            }
        } catch (e: Exception) {
            CancelOrderResult.Failure("Could not reach the store. Check your connection and try again.")
        } finally {
            _isCancelling.value = false
        }
    }

    private fun Response<*>.apiErrorMessage(fallback: String): String {
        val raw = errorBody()?.string().orEmpty()
        val parsed = runCatching {
            Json { ignoreUnknownKeys = true }.decodeFromString<ApiErrorResponse>(raw)
        }.getOrNull()
        return parsed?.message
            ?: parsed?.error
            ?: parsed?.msg
            ?: fallback
    }

    private fun formatDate(dateStr: String): String {
        return if (dateStr.length >= 10) dateStr.substring(0, 10) else dateStr
    }
}

@Serializable
private data class ApiErrorResponse(
    val error: String? = null,
    val message: String? = null,
    val msg: String? = null
)

data class OrderView(
    val id: String,
    val publicOrderNumber: String,
    val status: String,
    val grandTotalPaise: Long,
    val items: List<OrderItemView> = emptyList(),
    val createdAt: String
) {
    val canCancel: Boolean
        get() = status == "pending_review"
    val hasRemovedItems: Boolean
        get() = items.any { it.removed }
}

data class OrderItemView(
    val id: String,
    val name: String,
    val qty: Long,
    val lineTotalPaise: Long,
    val quantityType: String?,
    val unitLabel: String?,
    val removed: Boolean,
    val removedReason: String?
)
