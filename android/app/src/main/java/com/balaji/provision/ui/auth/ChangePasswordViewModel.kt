package com.balaji.provision.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.balaji.provision.data.auth.AuthRepository
import com.balaji.provision.data.auth.PasswordChangeResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChangePasswordUiState(
    val saving: Boolean = false,
    val error: String? = null,
    val done: Boolean = false
)

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ChangePasswordUiState())
    val state: StateFlow<ChangePasswordUiState> = _state.asStateFlow()

    fun submit(newPassword: String, confirmPassword: String) {
        if (_state.value.saving) return
        if (newPassword.length < 8) {
            _state.update { it.copy(error = "Use at least 8 characters.") }
            return
        }
        if (newPassword != confirmPassword) {
            _state.update { it.copy(error = "Passwords do not match.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            when (val result = authRepository.updatePassword(newPassword)) {
                PasswordChangeResult.Success -> {
                    _state.update { it.copy(saving = false, done = true) }
                }
                is PasswordChangeResult.Failure -> {
                    _state.update { it.copy(saving = false, error = result.message) }
                }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
