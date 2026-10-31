package com.balaji.provision.push

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Registers this device for push as soon as a session exists.
 *
 * At cold start the stored session and the FCM token can arrive in either order, so the
 * registrar is retried a few times instead of relying on one attempt.
 */
@HiltViewModel
class PushSyncViewModel @Inject constructor(
    private val registrar: PushRegistrar
) : ViewModel() {

    init {
        viewModelScope.launch {
            repeat(6) {
                if (registrar.sync()) return@launch
                delay(3_000)
            }
        }
    }
}
