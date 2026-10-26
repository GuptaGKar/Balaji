package com.balaji.provision.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.balaji.provision.data.auth.AuthRepository
import com.balaji.provision.data.repository.AccountRepository
import com.balaji.provision.data.repository.AccountResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SessionRoleState(
    val checking: Boolean = true,
    val signedIn: Boolean = false,
    val role: String? = null,
    val isActive: Boolean = true,
    val mustChangePassword: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SessionRoleViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _state = MutableStateFlow(
        SessionRoleState(signedIn = authRepository.session.value != null)
    )
    val state: StateFlow<SessionRoleState> = _state.asStateFlow()

    private var profileJob: Job? = null

    init {
        viewModelScope.launch {
            authRepository.session.collectLatest { session ->
                if (session == null) {
                    profileJob?.cancel()
                    _state.value = SessionRoleState(checking = false, signedIn = false)
                } else {
                    loadProfile()
                }
            }
        }
    }

    fun refresh() {
        if (authRepository.session.value == null) {
            _state.value = SessionRoleState(checking = false, signedIn = false)
            return
        }
        viewModelScope.launch { loadProfile() }
    }

    private suspend fun loadProfile() {
        profileJob?.cancel()
        _state.value = _state.value.copy(checking = true, signedIn = true, error = null)
        profileJob = viewModelScope.launch {
            when (val result = accountRepository.loadProfile()) {
                is AccountResult.Success -> {
                    _state.value = SessionRoleState(
                        checking = false,
                        signedIn = true,
                        role = result.value.role,
                        isActive = result.value.isActive,
                        mustChangePassword = result.value.mustChangePassword
                    )
                }
                is AccountResult.Failure -> {
                    _state.value = SessionRoleState(
                        checking = false,
                        signedIn = true,
                        error = result.message
                    )
                }
            }
        }
    }
}
