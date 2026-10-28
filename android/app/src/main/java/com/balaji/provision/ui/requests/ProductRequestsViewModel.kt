package com.balaji.provision.ui.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.balaji.provision.data.repository.ProductRequest
import com.balaji.provision.data.repository.RequestOutcome
import com.balaji.provision.data.repository.RequestsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProductRequestsViewModel @Inject constructor(
    private val repository: RequestsRepository
) : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val staffView: Boolean = false,
        val requests: List<ProductRequest> = emptyList(),
        val loadError: String? = null,
        val submitting: Boolean = false,
        val actionId: String? = null,
        val message: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun load(staffView: Boolean = false) {
        if (_state.value.loading) return
        _state.value = _state.value.copy(loading = true, staffView = staffView, loadError = null)
        viewModelScope.launch {
            when (val result = repository.listRequests(staffView)) {
                is RequestOutcome.Success -> _state.value = _state.value.copy(
                    loading = false,
                    requests = result.value
                )
                is RequestOutcome.Failure -> _state.value = _state.value.copy(
                    loading = false,
                    loadError = result.message
                )
            }
        }
    }

    fun create(
        itemName: String,
        quantityText: String?,
        note: String?,
        onCreated: () -> Unit = {}
    ) {
        if (_state.value.submitting) return
        _state.value = _state.value.copy(submitting = true)
        viewModelScope.launch {
            val result = repository.createRequest(itemName.trim(), quantityText, note)
            _state.value = _state.value.copy(
                submitting = false,
                message = when (result) {
                    is RequestOutcome.Success -> "Request sent. The store will review it."
                    is RequestOutcome.Failure -> result.message
                }
            )
            if (result is RequestOutcome.Success) {
                onCreated()
                load(_state.value.staffView)
            }
        }
    }

    fun update(requestId: String, status: String, staffReply: String?) {
        if (_state.value.actionId != null) return
        _state.value = _state.value.copy(actionId = requestId)
        viewModelScope.launch {
            val result = repository.updateRequest(requestId, status, staffReply)
            _state.value = _state.value.copy(
                actionId = null,
                message = when (result) {
                    is RequestOutcome.Success -> "Request updated."
                    is RequestOutcome.Failure -> result.message
                }
            )
            if (result is RequestOutcome.Success) {
                load(_state.value.staffView)
            }
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    /** One review per delivered order; the callback runs after the server answers. */
    fun reviewOrder(
        orderId: String,
        rating: Int,
        comment: String?,
        onDone: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            when (val result = repository.submitReview(orderId, rating, comment)) {
                is RequestOutcome.Success -> onDone(true, "Thanks for reviewing your order.")
                is RequestOutcome.Failure -> onDone(false, result.message)
            }
        }
    }

    /** Uploads the optional order photo, then attaches it to the freshly placed order. */
    fun attachPhoto(
        orderId: String,
        bytes: ByteArray,
        mimeType: String,
        onDone: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            when (val upload = repository.uploadOrderImage(bytes, mimeType)) {
                is RequestOutcome.Failure -> onDone(false, upload.message)
                is RequestOutcome.Success -> {
                    when (val attach = repository.attachOrderImage(orderId, upload.value)) {
                        is RequestOutcome.Success -> onDone(true, "Photo attached to your order.")
                        is RequestOutcome.Failure -> onDone(false, attach.message)
                    }
                }
            }
        }
    }
}
