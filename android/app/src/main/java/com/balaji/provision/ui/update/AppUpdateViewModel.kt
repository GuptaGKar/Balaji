package com.balaji.provision.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.balaji.provision.data.network.AppReleaseResponse
import com.balaji.provision.data.update.AppUpdateManager
import com.balaji.provision.data.update.UpdateState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val appUpdateManager: AppUpdateManager
) : ViewModel() {

    val state: StateFlow<UpdateState> = appUpdateManager.updateState

    init {
        checkForUpdates()
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            appUpdateManager.checkForUpdates()
        }
    }

    fun downloadAndInstall(release: AppReleaseResponse) {
        viewModelScope.launch {
            appUpdateManager.downloadAndInstall(release)
        }
    }

    fun retryInstall(file: File) {
        appUpdateManager.triggerInstall(file)
    }

    fun dismiss() {
        appUpdateManager.dismiss()
    }
}
