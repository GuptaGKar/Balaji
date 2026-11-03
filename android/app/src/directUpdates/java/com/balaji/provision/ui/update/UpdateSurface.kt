package com.balaji.provision.ui.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.balaji.provision.data.update.UpdateState

@Composable
fun UpdateSurface() {
    val updateViewModel: AppUpdateViewModel = hiltViewModel()
    val updateState by updateViewModel.state.collectAsState()
    InAppUpdateDialog(
        updateState = updateState,
        onUpdateClicked = {
            val current = updateState
            if (current is UpdateState.UpdateAvailable) {
                updateViewModel.downloadAndInstall(current.release)
            }
        },
        onInstallClicked = {
            val current = updateState
            if (current is UpdateState.ReadyToInstall) {
                updateViewModel.retryInstall(current.apkFile)
            }
        },
        onDismissClicked = updateViewModel::dismiss
    )
}
