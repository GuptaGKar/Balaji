package com.balaji.provision.ui.driver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.balaji.provision.data.repository.RiderRepository
import com.balaji.provision.data.repository.RiderResult
import com.balaji.provision.domain.model.RiderOrder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DriverUiState(
    val orders: List<RiderOrder> = emptyList(),
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val updatingOrderId: String? = null,
    val message: String? = null,
    val actionError: String? = null
)

@HiltViewModel
class DriverViewModel @Inject constructor(
    private val riderRepository: RiderRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DriverUiState())
    val state: StateFlow<DriverUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var pollJob: Job? = null

    init {
        load(silent = false)
    }

    fun startPolling() {
        if (pollJob?.isActive == true) return
        load(silent = true)
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MILLIS)
                load(silent = true)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    fun refresh() {
        load(silent = false)
    }

    fun updateStatus(order: RiderOrder, toStatus: String, note: String? = null) {
        if (_state.value.updatingOrderId != null) return
        viewModelScope.launch {
            _state.update { it.copy(updatingOrderId = order.id, actionError = null) }
            when (val result = riderRepository.updateStatus(order.id, toStatus, note)) {
                is RiderResult.Success -> {
                    _state.update {
                        it.copy(
                            updatingOrderId = null,
                            message = statusMessage(toStatus)
                        )
                    }
                    load(silent = true, force = true)
                }
                is RiderResult.Failure -> {
                    _state.update {
                        it.copy(updatingOrderId = null, actionError = result.message)
                    }
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

    private fun load(silent: Boolean, force: Boolean = false) {
        if (loadJob?.isActive == true && !force) return
        if (force) loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (!silent) {
                _state.update {
                    it.copy(
                        isInitialLoading = it.orders.isEmpty(),
                        isRefreshing = it.orders.isNotEmpty(),
                        error = null
                    )
                }
            }
            when (val result = riderRepository.loadOrders()) {
                is RiderResult.Success -> {
                    _state.update {
                        it.copy(
                            orders = result.value,
                            isInitialLoading = false,
                            isRefreshing = false,
                            error = null
                        )
                    }
                }
                is RiderResult.Failure -> {
                    _state.update {
                        val showError = !silent || it.orders.isEmpty()
                        it.copy(
                            isInitialLoading = false,
                            isRefreshing = false,
                            error = if (showError) result.message else it.error
                        )
                    }
                }
            }
        }
    }

    private fun statusMessage(toStatus: String): String = when (toStatus) {
        "out_for_delivery" -> "Delivery started."
        "delivered" -> "Order marked delivered."
        "delivery_failed" -> "Delivery marked failed."
        else -> "Delivery updated."
    }

    private companion object {
        const val POLL_INTERVAL_MILLIS = 20_000L
    }
}
