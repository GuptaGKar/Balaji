package com.balaji.provision.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.balaji.provision.data.auth.AuthRepository
import com.balaji.provision.data.auth.AuthResult
import com.balaji.provision.data.auth.AuthSession
import com.balaji.provision.data.repository.CartRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val session: AuthSession? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val notice: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val cartRepository: CartRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState(session = authRepository.session.value))
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.session.collect { session ->
                _state.value = _state.value.copy(session = session)
            }
        }
    }

    fun signIn(email: String, password: String) {
        submit(isSignUp = false, email = email, password = password)
    }

    fun signUp(email: String, password: String) {
        submit(isSignUp = true, email = email, password = password)
    }

    private fun submit(isSignUp: Boolean, email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _state.value = _state.value.copy(error = "Enter your email and password.")
            return
        }
        if (isSignUp && password.length < 8) {
            _state.value = _state.value.copy(error = "Password must be at least 8 characters.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, notice = null)
            val result = if (isSignUp) {
                authRepository.signUp(email, password)
            } else {
                authRepository.signIn(email, password)
            }
            _state.value = when (result) {
                is AuthResult.Success -> {
                    if (result.session == null) {
                        _state.value.copy(
                            isLoading = false,
                            notice = "Account created. Check your email to confirm before signing in."
                        )
                    } else {
                        _state.value.copy(isLoading = false, session = result.session)
                    }
                }
                is AuthResult.Failure -> _state.value.copy(isLoading = false, error = result.message)
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            cartRepository.clearCart()
            authRepository.signOut()
            _state.value = AuthUiState()
        }
    }

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, notice = null)
    }
}
