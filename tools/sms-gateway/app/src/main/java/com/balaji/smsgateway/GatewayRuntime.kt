package com.balaji.smsgateway

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface GatewayState {
    data object Stopped : GatewayState

    data class Running(val port: Int) : GatewayState

    data class Failed(val reason: String) : GatewayState
}

object GatewayRuntime {
    private val _state = MutableStateFlow<GatewayState>(GatewayState.Stopped)
    val state: StateFlow<GatewayState> = _state.asStateFlow()

    internal fun running(port: Int) {
        _state.value = GatewayState.Running(port)
    }

    internal fun failed(reason: String) {
        _state.value = GatewayState.Failed(reason)
    }

    internal fun stopped() {
        _state.value = GatewayState.Stopped
    }
}
