package com.balaji.provision.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.balaji.provision.data.location.DeliveryZone
import com.balaji.provision.data.location.GeoPoint
import com.balaji.provision.data.location.LocationFixSource
import com.balaji.provision.data.repository.DeliveryAreas
import com.balaji.provision.data.repository.DeliveryAreaSource
import com.balaji.provision.data.repository.DeliveryAvailability
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where the spot check stands. Only the server can produce [Answered]. */
sealed interface SpotCheck {
    data object Idle : SpotCheck
    data object Checking : SpotCheck
    data class Answered(val availability: DeliveryAvailability) : SpotCheck
    data class Failed(val message: String) : SpotCheck
}

data class AddressMapUiState(
    val point: GeoPoint? = null,
    /** Set when the map must move on its own; [cameraRequestId] makes each move once. */
    val cameraTarget: GeoPoint? = null,
    val cameraRequestId: Long = 0L,
    val zones: List<DeliveryZone> = emptyList(),
    val zonesLoaded: Boolean = false,
    val check: SpotCheck = SpotCheck.Idle,
    val isLocating: Boolean = false,
    val notice: String? = null
) {
    val selectedZoneId: String?
        get() = (check as? SpotCheck.Answered)?.availability?.zoneId

    val answered: DeliveryAvailability?
        get() = (check as? SpotCheck.Answered)?.availability
}

/**
 * Holds the state for the delivery pin picker.
 *
 * The map is an input device and a picture: the only thing that decides whether this
 * spot can be delivered to, and what it costs, is the server answer in [SpotCheck.Answered].
 * Polygons are drawn to help the customer aim, never to grade their order.
 */
@HiltViewModel
class AddressMapPickerViewModel @Inject constructor(
    private val repository: DeliveryAreaSource,
    private val locationRepository: LocationFixSource
) : ViewModel() {

    private val _state = MutableStateFlow(AddressMapUiState())
    val state: StateFlow<AddressMapUiState> = _state.asStateFlow()

    private var checkJob: Job? = null
    private var checkSequence: Long = 0L
    private var cameraSequence: Long = 0L

    fun openAt(point: GeoPoint?, pin: String, subtotalPaise: Long = 0L) {
        if (!_state.value.zonesLoaded) loadZones()
        val known = point ?: _state.value.point
        if (known != null) {
            moveTo(known, moveCamera = point != null)
            requestCheck(pin, known, subtotalPaise)
        }
    }

    fun loadZones() {
        viewModelScope.launch {
            when (val areas = repository.loadZones()) {
                is DeliveryAreas.Loaded -> _state.update {
                    it.copy(zones = areas.zones, zonesLoaded = true)
                }
                DeliveryAreas.Unavailable -> _state.update {
                    it.copy(zonesLoaded = true, notice = ZONES_UNAVAILABLE_NOTICE)
                }
            }
        }
    }

    /** The map stopped moving under the customer's finger. */
    fun onCameraIdle(point: GeoPoint, pin: String, subtotalPaise: Long = 0L) {
        // Programmatic camera moves also land here. Ignore a repeat of the spot we
        // already hold so the banner does not flicker or double-check.
        if (sameSpot(_state.value.point, point) && _state.value.check != SpotCheck.Idle) return
        moveTo(point, moveCamera = false)
        requestCheck(pin, point, subtotalPaise)
    }

    /** The PIN in the form changed while the map was open; ask again about the same spot. */
    fun recheck(pin: String, subtotalPaise: Long = 0L) {
        val point = _state.value.point ?: return
        requestCheck(pin, point, subtotalPaise)
    }

    fun useMyLocation(pin: String, subtotalPaise: Long = 0L) {
        if (_state.value.isLocating) return
        _state.update { it.copy(isLocating = true, notice = null) }
        viewModelScope.launch {
            locationRepository.currentLocation()
                .onSuccess { point ->
                    _state.update { it.copy(isLocating = false) }
                    moveTo(point, moveCamera = true)
                    requestCheck(pin, point, subtotalPaise)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLocating = false,
                            notice = error.message ?: LOCATION_UNAVAILABLE_NOTICE
                        )
                    }
                }
        }
    }

    /** Clears the notice without disturbing the answer the customer already has. */
    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    /**
     * The form is opening for a different address, so the previous spot must not be
     * mistaken for this one. Loaded zones are kept: they are the store's map, not the
     * customer's.
     */
    fun reset() {
        checkJob?.cancel()
        checkSequence += 1L
        _state.value = AddressMapUiState(
            zones = _state.value.zones,
            zonesLoaded = _state.value.zonesLoaded
        )
    }

    private fun moveTo(point: GeoPoint, moveCamera: Boolean) {
        _state.update { current ->
            if (moveCamera) {
                cameraSequence += 1L
                current.copy(point = point, cameraTarget = point, cameraRequestId = cameraSequence)
            } else {
                current.copy(point = point)
            }
        }
    }

    /**
     * Debounced so dragging the map across the city issues one request, not fifty.
     * [checkSequence] rejects a slow reply that describes a spot the customer has
     * already left.
     */
    private fun requestCheck(pin: String, point: GeoPoint, subtotalPaise: Long) {
        checkJob?.cancel()
        checkSequence += 1L
        val sequence = checkSequence

        if (pin.isBlank()) {
            _state.update {
                it.copy(check = SpotCheck.Idle, notice = BLANK_PIN_NOTICE)
            }
            return
        }

        _state.update { it.copy(check = SpotCheck.Checking, notice = null) }
        checkJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            val result = repository.availability(pin = pin, point = point, subtotalPaise = subtotalPaise)
            if (sequence != checkSequence) return@launch
            _state.update { current ->
                result.fold(
                    onSuccess = { current.copy(check = SpotCheck.Answered(it)) },
                    onFailure = {
                        current.copy(
                            check = SpotCheck.Failed(it.message ?: CHECK_FAILED_NOTICE)
                        )
                    }
                )
            }
        }
    }

    private fun sameSpot(a: GeoPoint?, b: GeoPoint?): Boolean {
        if (a == null || b == null) return false
        return kotlin.math.abs(a.latitude - b.latitude) < COORD_EPSILON &&
            kotlin.math.abs(a.longitude - b.longitude) < COORD_EPSILON
    }

    private companion object {
        const val DEBOUNCE_MS = 500L
        const val COORD_EPSILON = 1e-6
        const val BLANK_PIN_NOTICE = "Enter the PIN code to check delivery for this spot."
        const val CHECK_FAILED_NOTICE = "Could not check delivery for this spot."
        const val ZONES_UNAVAILABLE_NOTICE = "Delivery areas could not be loaded."
        const val LOCATION_UNAVAILABLE_NOTICE = "Could not get your current location."
    }
}
