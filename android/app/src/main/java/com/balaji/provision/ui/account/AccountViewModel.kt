package com.balaji.provision.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.balaji.provision.data.auth.AuthRepository
import com.balaji.provision.data.repository.AccountRepository
import com.balaji.provision.data.repository.AccountResult
import com.balaji.provision.domain.model.AddressDraft
import com.balaji.provision.domain.model.CustomerProfile
import com.balaji.provision.domain.model.SavedAddress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountUiState(
    val profile: CustomerProfile? = null,
    val addresses: List<SavedAddress> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val hasLoaded: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val repository: AccountRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AccountUiState())
    val state: StateFlow<AccountUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.session.collect { session ->
                if (session == null) {
                    _state.value = AccountUiState()
                } else {
                    refresh()
                }
            }
        }
    }

    fun refresh(force: Boolean = false) {
        if (authRepository.session.value == null) {
            _state.value = AccountUiState()
            return
        }
        if (_state.value.isLoading && !force) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, isSaving = false, error = null)
            var error: String? = null
            when (val profile = repository.loadProfile()) {
                is AccountResult.Success -> _state.value = _state.value.copy(profile = profile.value)
                is AccountResult.Failure -> error = profile.message
            }
            when (val addresses = repository.loadAddresses()) {
                is AccountResult.Success -> _state.value = _state.value.copy(addresses = addresses.value)
                is AccountResult.Failure -> error = error ?: addresses.message
            }
            _state.value = _state.value.copy(isLoading = false, hasLoaded = true, error = error)
        }
    }

    fun updateDisplayName(
        displayName: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        if (_state.value.isSaving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, error = null)
            when (val result = repository.updateDisplayName(displayName)) {
                is AccountResult.Success -> {
                    _state.value = _state.value.copy(
                        profile = result.value,
                        isSaving = false
                    )
                    onResult(true, null)
                }
                is AccountResult.Failure -> {
                    _state.value = _state.value.copy(isSaving = false)
                    onResult(false, result.message)
                }
            }
        }
    }

    fun saveAddress(
        draft: AddressDraft,
        addressId: String?,
        onResult: (Boolean, SavedAddress?, String?) -> Unit
    ) {
        if (_state.value.isSaving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, error = null)
            when (val result = repository.saveAddress(draft, addressId)) {
                is AccountResult.Success -> {
                    val saved = result.value
                    _state.value = _state.value.copy(
                        isSaving = false,
                        addresses = mergeAddress(saved)
                    )
                    onResult(true, saved, null)
                }
                is AccountResult.Failure -> {
                    _state.value = _state.value.copy(isSaving = false)
                    onResult(false, null, result.message)
                }
            }
        }
    }

    fun setDefaultAddress(addressId: String) {
        if (_state.value.isSaving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, error = null)
            when (val result = repository.setDefaultAddress(addressId)) {
                is AccountResult.Success -> {
                    _state.value = _state.value.copy(isSaving = false)
                    refresh(force = true)
                }
                is AccountResult.Failure -> _state.value = _state.value.copy(
                    isSaving = false,
                    error = result.message
                )
            }
        }
    }

    fun deleteAddress(addressId: String) {
        if (_state.value.isSaving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, error = null)
            when (val result = repository.deleteAddress(addressId)) {
                is AccountResult.Success -> {
                    _state.value = _state.value.copy(isSaving = false)
                    refresh(force = true)
                }
                is AccountResult.Failure -> _state.value = _state.value.copy(
                    isSaving = false,
                    error = result.message
                )
            }
        }
    }

    fun deleteAccount(confirmation: String, onResult: (Boolean, String?) -> Unit) {
        if (_state.value.isDeleting) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isDeleting = true, error = null)
            when (val result = repository.deleteAccount(confirmation)) {
                is AccountResult.Success -> {
                    _state.value = AccountUiState(hasLoaded = true)
                    onResult(true, null)
                }
                is AccountResult.Failure -> {
                    _state.value = _state.value.copy(isDeleting = false)
                    onResult(false, result.message)
                }
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun mergeAddress(saved: SavedAddress): List<SavedAddress> {
        val existing = _state.value.addresses
        val merged = if (existing.any { it.id == saved.id }) {
            existing.map { if (it.id == saved.id) saved else it }
        } else {
            existing + saved
        }
        return merged
            .map { address ->
                if (saved.isDefault && address.id != saved.id) {
                    address.copy(isDefault = false)
                } else {
                    address
                }
            }
            .sortedWith(compareByDescending<SavedAddress> { it.isDefault }.thenBy { it.label })
    }
}
