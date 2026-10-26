package com.balaji.provision.ui.manager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.balaji.provision.data.repository.ManagerRepository
import com.balaji.provision.data.repository.ManagerResult
import com.balaji.provision.domain.model.LowStockProduct
import com.balaji.provision.domain.model.ManagerOrder
import com.balaji.provision.domain.model.ManagerOrderLine
import com.balaji.provision.domain.model.Product
import com.balaji.provision.domain.model.RiderOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ManagerUiState(
    val orders: List<ManagerOrder> = emptyList(),
    val lowStock: List<LowStockProduct> = emptyList(),
    val riders: List<RiderOption> = emptyList(),
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val selectedOrderId: String? = null,
    val selectedItems: List<ManagerOrderLine> = emptyList(),
    val isDetailLoading: Boolean = false,
    val detailError: String? = null,
    val isActionInProgress: Boolean = false,
    val isRiderListLoading: Boolean = false,
    val adjustingProductId: String? = null,
    val products: List<Product> = emptyList(),
    val isProductsLoading: Boolean = false,
    val updatingPriceProductId: String? = null,
    val message: String? = null,
    val actionError: String? = null
) {
    val selectedOrder: ManagerOrder?
        get() = orders.firstOrNull { it.id == selectedOrderId }

    val activeOrderCount: Int
        get() = orders.count { it.status !in setOf("delivered", "cancelled", "rejected", "delivery_failed") }

    val pendingReviewCount: Int
        get() = orders.count { it.status == "pending_review" }

    val readyForDispatchCount: Int
        get() = orders.count { it.status == "ready_for_dispatch" }
}

@HiltViewModel
class ManagerViewModel @Inject constructor(
    private val repository: ManagerRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ManagerUiState())
    val state: StateFlow<ManagerUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val hasData = _state.value.orders.isNotEmpty() || _state.value.lowStock.isNotEmpty()
            _state.update {
                it.copy(
                    isInitialLoading = !hasData,
                    isRefreshing = hasData,
                    error = null
                )
            }
            when (val result = repository.loadOrders()) {
                is ManagerResult.Success -> {
                    _state.update {
                        it.copy(
                            orders = result.value,
                            isInitialLoading = false,
                            isRefreshing = false,
                            error = null
                        )
                    }
                }
                is ManagerResult.Failure -> {
                    _state.update {
                        it.copy(
                            isInitialLoading = false,
                            isRefreshing = false,
                            error = result.message
                        )
                    }
                    return@launch
                }
            }
            when (val result = repository.loadLowStock()) {
                is ManagerResult.Success -> _state.update {
                    it.copy(lowStock = result.value, error = null)
                }
                is ManagerResult.Failure -> _state.update {
                    it.copy(error = it.error ?: result.message)
                }
            }
            when (val result = repository.loadAllProducts()) {
                is ManagerResult.Success -> _state.update {
                    it.copy(products = result.value)
                }
                is ManagerResult.Failure -> { /* ignore optional catalogue fetch error */ }
            }
        }
    }

    fun openOrder(orderId: String) {
        _state.update {
            it.copy(
                selectedOrderId = orderId,
                selectedItems = emptyList(),
                isDetailLoading = true,
                detailError = null
            )
        }
        viewModelScope.launch {
            when (val result = repository.loadOrderItems(orderId)) {
                is ManagerResult.Success -> _state.update {
                    if (it.selectedOrderId != orderId) it else it.copy(
                        selectedItems = result.value,
                        isDetailLoading = false
                    )
                }
                is ManagerResult.Failure -> _state.update {
                    if (it.selectedOrderId != orderId) it else it.copy(
                        selectedItems = emptyList(),
                        isDetailLoading = false,
                        detailError = result.message
                    )
                }
            }
        }
    }

    fun closeOrder() {
        _state.update {
            it.copy(
                selectedOrderId = null,
                selectedItems = emptyList(),
                detailError = null,
                isDetailLoading = false
            )
        }
    }

    fun reloadOrderItems() {
        _state.value.selectedOrderId?.let(::openOrder)
    }

    fun removeOrderItem(item: ManagerOrderLine, reason: String) {
        val order = _state.value.selectedOrder ?: return
        if (_state.value.isActionInProgress) return
        if (reason.isBlank()) {
            _state.update { it.copy(actionError = "A reason is required to remove an item.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isActionInProgress = true, actionError = null) }
            when (val result = repository.removeOrderItem(order.id, item.id, reason)) {
                is ManagerResult.Success -> {
                    _state.update {
                        it.copy(
                            isActionInProgress = false,
                            message = if (result.value) {
                                "Item removed. No items remain, so the order was cancelled."
                            } else {
                                "${item.name} removed and the order total was recalculated."
                            }
                        )
                    }
                    refresh()
                    openOrder(order.id)
                }
                is ManagerResult.Failure -> _state.update {
                    it.copy(isActionInProgress = false, actionError = result.message)
                }
            }
        }
    }

    fun transitionOrder(toStatus: String, reason: String? = null) {
        val order = _state.value.selectedOrder ?: return
        if (_state.value.isActionInProgress) return
        viewModelScope.launch {
            _state.update { it.copy(isActionInProgress = true, actionError = null) }
            when (val result = repository.transitionOrder(order.id, toStatus, reason)) {
                is ManagerResult.Success -> {
                    _state.update {
                        it.copy(
                            isActionInProgress = false,
                            message = statusMessage(toStatus)
                        )
                    }
                    refresh()
                    openOrder(order.id)
                }
                is ManagerResult.Failure -> _state.update {
                    it.copy(isActionInProgress = false, actionError = result.message)
                }
            }
        }
    }

    fun loadRiders() {
        if (_state.value.isRiderListLoading || _state.value.riders.isNotEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isRiderListLoading = true, actionError = null) }
            when (val result = repository.loadRiders()) {
                is ManagerResult.Success -> _state.update {
                    it.copy(riders = result.value, isRiderListLoading = false)
                }
                is ManagerResult.Failure -> _state.update {
                    it.copy(isRiderListLoading = false, actionError = result.message)
                }
            }
        }
    }

    fun assignRider(riderId: String) {
        val order = _state.value.selectedOrder ?: return
        if (_state.value.isActionInProgress) return
        viewModelScope.launch {
            _state.update { it.copy(isActionInProgress = true, actionError = null) }
            when (val result = repository.assignOrder(order.id, riderId)) {
                is ManagerResult.Success -> _state.update {
                    it.copy(isActionInProgress = false, message = "Rider assigned to ${order.orderNumber}.")
                }
                is ManagerResult.Failure -> _state.update {
                    it.copy(isActionInProgress = false, actionError = result.message)
                }
            }
        }
    }

    fun adjustStock(product: LowStockProduct, deltaQty: Long, note: String?) {
        if (deltaQty == 0L || _state.value.adjustingProductId != null) return
        viewModelScope.launch {
            _state.update { it.copy(adjustingProductId = product.id, actionError = null) }
            when (val result = repository.adjustStock(
                productId = product.id,
                deltaQty = deltaQty,
                reason = "manual_adjustment",
                note = note
            )) {
                is ManagerResult.Success -> {
                    _state.update {
                        it.copy(
                            adjustingProductId = null,
                            message = "${product.name}: stock updated to ${result.value}."
                        )
                    }
                    refresh()
                }
                is ManagerResult.Failure -> _state.update {
                    it.copy(adjustingProductId = null, actionError = result.message)
                }
            }
        }
    }

    fun updateProductPrice(product: Product, sellingPricePaise: Long, mrpPaise: Long?) {
        updateProductPriceById(product.id, product.name, sellingPricePaise, mrpPaise)
    }

    fun updateProductPriceById(productId: String, productName: String, sellingPricePaise: Long, mrpPaise: Long?) {
        if (_state.value.updatingPriceProductId != null) return
        viewModelScope.launch {
            _state.update { it.copy(updatingPriceProductId = productId, actionError = null) }
            when (val result = repository.updateProductPrice(
                productId = productId,
                pricePaise = sellingPricePaise,
                mrpPaise = mrpPaise
            )) {
                is ManagerResult.Success -> {
                    _state.update {
                        it.copy(
                            updatingPriceProductId = null,
                            message = "$productName: price updated successfully."
                        )
                    }
                    refresh()
                }
                is ManagerResult.Failure -> _state.update {
                    it.copy(updatingPriceProductId = null, actionError = result.message)
                }
            }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    fun consumeActionError() {
        _state.update { it.copy(actionError = null) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    private fun statusMessage(toStatus: String): String = when (toStatus) {
        "accepted" -> "Order accepted."
        "rejected" -> "Order rejected and stock restored."
        "preparing" -> "Order preparing."
        "ready_for_dispatch" -> "Order is ready for dispatch."
        "out_for_delivery" -> "Order dispatched."
        "delivered" -> "Order marked delivered."
        "delivery_failed" -> "Delivery marked failed."
        "cancelled" -> "Order cancelled and stock restored."
        else -> "Order updated."
    }
}
