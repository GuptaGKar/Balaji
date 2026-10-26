package com.balaji.provision.ui.staff

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.balaji.provision.ui.auth.AuthViewModel
import com.balaji.provision.ui.auth.ChangePasswordScreen
import com.balaji.provision.ui.driver.DriverScreen
import com.balaji.provision.ui.manager.ManagerScreen
import com.balaji.provision.ui.session.SessionGateScreen
import com.balaji.provision.ui.session.SessionLoadingScreen
import com.balaji.provision.ui.session.SessionRoleViewModel

@Composable
fun StaffApp() {
    val roleViewModel: SessionRoleViewModel = hiltViewModel()
    val authViewModel: AuthViewModel = hiltViewModel()
    val role by roleViewModel.state.collectAsState()

    when {
        !role.signedIn -> StaffLoginScreen(viewModel = authViewModel)
        role.checking -> SessionLoadingScreen()
        role.error != null -> SessionGateScreen(
            title = "Could not load your account",
            message = role.error!!,
            onSignOut = authViewModel::signOut,
            primaryLabel = "Retry",
            onPrimary = roleViewModel::refresh
        )
        !role.isActive -> SessionGateScreen(
            title = "Account deactivated",
            message = "This account is no longer active. Contact the store for help.",
            onSignOut = authViewModel::signOut
        )
        role.mustChangePassword -> ChangePasswordScreen(
            onPasswordChanged = roleViewModel::refresh,
            onSignOut = authViewModel::signOut
        )
        role.role == "delivery" -> DriverScreen(
            onSignOut = authViewModel::signOut
        )
        role.role == "manager" || role.role == "staff" -> ManagerScreen(
            role = role.role!!,
            onSignOut = authViewModel::signOut
        )
        role.role == "customer" -> SessionGateScreen(
            title = "Use the customer app",
            message = "Customer accounts use the separate Balaji Provision Store app.",
            onSignOut = authViewModel::signOut
        )
        else -> SessionGateScreen(
            title = "Account unavailable",
            message = "Your staff role could not be loaded. Contact the store for help.",
            onSignOut = authViewModel::signOut,
            primaryLabel = "Retry",
            onPrimary = roleViewModel::refresh
        )
    }
}
