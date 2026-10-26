package com.balaji.provision.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.balaji.provision.data.auth.AuthRepository
import com.balaji.provision.data.auth.AuthResult
import com.balaji.provision.data.auth.AuthSession
import com.balaji.provision.data.auth.OtpResult
import com.balaji.provision.data.auth.PhoneNumber
import com.balaji.provision.data.repository.CartRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PhoneAuthStep {
    PHONE,
    OTP
}

data class AuthUiState(
    val session: AuthSession? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val step: PhoneAuthStep = PhoneAuthStep.PHONE,
    val phone: String = "",
    val resendSeconds: Int = 0
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val cartRepository: CartRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState(session = authRepository.session.value))
    val state: StateFlow<AuthUiState> = _state.asStateFlow()
    private var resendJob: Job? = null

    init {
        viewModelScope.launch {
            authRepository.session.collect { session ->
                _state.value = _state.value.copy(session = session)
            }
        }
    }

    fun sendOtp(phoneInput: String, createUser: Boolean = true) {
        if (_state.value.isLoading) return
        if (_state.value.step == PhoneAuthStep.OTP && _state.value.resendSeconds > 0) return
        val phone = PhoneNumber.normalizeIndian(phoneInput)
        if (phone == null) {
            _state.value = _state.value.copy(error = "Enter a valid 10-digit Indian mobile number.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = true,
                error = null,
                notice = null,
                phone = phone
            )
            when (val result = authRepository.requestOtp(phone, createUser)) {
                OtpResult.Sent -> {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        step = PhoneAuthStep.OTP,
                        notice = if (createUser) {
                            "OTP sent to $phone to create your account."
                        } else {
                            "OTP sent to $phone."
                        },
                        resendSeconds = 60
                    )
                    startResendTimer()
                }
                is OtpResult.Failure -> {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
            }
        }
    }

    fun verifyOtp(token: String) {
        if (_state.value.isLoading) return
        val phone = _state.value.phone
        if (phone.isBlank()) {
            _state.value = _state.value.copy(error = "Enter your mobile number and request a new OTP.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, notice = null)
            when (val result = authRepository.verifyOtp(phone, token)) {
                is AuthResult.Success -> {
                    resendJob?.cancel()
                    _state.value = _state.value.copy(
                        isLoading = false,
                        session = result.session,
                        error = null,
                        notice = null,
                        resendSeconds = 0
                    )
                }
                is AuthResult.Failure -> {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
            }
        }
    }

    fun signInWithPassword(loginId: String, password: String) {
        if (_state.value.isLoading) return
        viewModelScope.launch {
            resendJob?.cancel()
            _state.value = _state.value.copy(
                isLoading = true,
                error = null,
                notice = null,
                resendSeconds = 0
            )
            when (val result = authRepository.signInWithPassword(loginId, password)) {
                is AuthResult.Success -> {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        session = result.session,
                        error = null,
                        notice = null
                    )
                }
                is AuthResult.Failure -> {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
            }
        }
    }

    fun changePhone() {
        resendJob?.cancel()
        _state.value = _state.value.copy(
            step = PhoneAuthStep.PHONE,
            isLoading = false,
            error = null,
            notice = null,
            resendSeconds = 0
        )
    }

    fun signOut() {
        resendJob?.cancel()
        viewModelScope.launch {
            cartRepository.clearCart()
            authRepository.signOut()
            _state.value = AuthUiState()
        }
    }

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, notice = null)
    }

    private fun startResendTimer() {
        resendJob?.cancel()
        resendJob = viewModelScope.launch {
            repeat(60) {
                delay(1_000L)
                _state.value = _state.value.copy(
                    resendSeconds = (_state.value.resendSeconds - 1).coerceAtLeast(0)
                )
            }
        }
    }
}
