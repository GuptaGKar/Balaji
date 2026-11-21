package com.balaji.provision.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.balaji.provision.data.location.GeoPoint
import com.balaji.provision.data.location.GeocodedPlace
import com.balaji.provision.data.location.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LocationUiState(
    val isLocating: Boolean = false,
    val fix: GeoPoint? = null,
    val place: GeocodedPlace? = null,
    val error: String? = null,
    /**
     * Bumped once per fetch. Callers key their "a fix arrived" effect on this rather than on the
     * coordinates, because a second lookup from the same spot produces an equal [GeoPoint] and
     * would otherwise never wake them up.
     */
    val requestId: Int = 0
)

@HiltViewModel
class AddressLocationViewModel @Inject constructor(
    private val repository: LocationRepository
) : ViewModel() {

    private val _state = MutableStateFlow(LocationUiState())
    val state: StateFlow<LocationUiState> = _state.asStateFlow()

    fun useCurrentLocation() {
        if (_state.value.isLocating) return
        val requestId = _state.value.requestId + 1
        _state.update { it.copy(isLocating = true, error = null, requestId = requestId) }
        viewModelScope.launch {
            repository.currentLocation()
                .onSuccess { point ->
                    val place = repository.describe(point)
                    _state.value = LocationUiState(
                        isLocating = false,
                        fix = point,
                        place = place,
                        requestId = requestId
                    )
                }
                .onFailure { error ->
                    _state.value = LocationUiState(
                        isLocating = false,
                        error = when (error) {
                            is SecurityException -> "Location permission is required."
                            else -> error.message
                                ?: "Could not get your location. Check GPS and try again."
                        },
                        requestId = requestId
                    )
                }
        }
    }

    fun onPermissionDenied() {
        _state.update {
            it.copy(
                error = "Location permission denied. You can still enter the address manually."
            )
        }
    }

    fun reset() {
        _state.value = LocationUiState()
    }
}
