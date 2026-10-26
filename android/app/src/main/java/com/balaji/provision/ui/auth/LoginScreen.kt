package com.balaji.provision.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.balaji.provision.ui.profile.LegalPage
import com.balaji.provision.ui.profile.LegalScreen

private enum class AuthMode {
    CHOOSE,
    SIGN_IN,
    SIGN_UP
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LoginScreen(
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var mode by rememberSaveable { mutableStateOf(AuthMode.CHOOSE) }
    var phone by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var legalPage by remember { mutableStateOf<LegalPage?>(null) }
    val isOtpStep = state.step == PhoneAuthStep.OTP
    val keyboard = LocalSoftwareKeyboardController.current
    val creatingAccount = mode == AuthMode.SIGN_UP

    LaunchedEffect(state.step) {
        if (state.step == PhoneAuthStep.OTP) otp = ""
    }

    legalPage?.let { page ->
        LegalScreen(page = page, onBack = { legalPage = null })
        return
    }

    fun chooseMode(target: AuthMode) {
        mode = target
        otp = ""
        viewModel.changePhone()
        viewModel.clearMessages()
    }

    fun submitOtpRequest() {
        keyboard?.hide()
        viewModel.sendOtp(phone, createUser = creatingAccount)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (mode != AuthMode.CHOOSE) {
                IconButton(onClick = { chooseMode(AuthMode.CHOOSE) }) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back to sign in options",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Text(
                "Customer account",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Store,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Column {
                    Text(
                        "Balaji Provision Store",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        when (mode) {
                            AuthMode.CHOOSE -> "Sign in or create an account"
                            AuthMode.SIGN_IN -> "Sign in to continue"
                            AuthMode.SIGN_UP -> "Create your customer account"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        if (mode == AuthMode.CHOOSE) {
                            "Sign in to your existing account or create a new one with your mobile number."
                        } else {
                            "One mobile number is enough to order groceries, save delivery addresses and track your orders."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            when {
                mode == AuthMode.CHOOSE -> {
                    Text(
                        "Welcome",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Choose how you want to continue.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = { chooseMode(AuthMode.SIGN_IN) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Sign in", style = MaterialTheme.typography.titleSmall)
                    }

                    OutlinedButton(
                        onClick = { chooseMode(AuthMode.SIGN_UP) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Create account", style = MaterialTheme.typography.titleSmall)
                    }
                }

                !isOtpStep -> {
                    Text(
                        if (creatingAccount) "Create your account" else "Sign in",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        if (creatingAccount) {
                            "Enter your mobile number. We will send a one-time password to verify it."
                        } else {
                            "Enter the mobile number linked to your account."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { input ->
                            phone = input.filter { it.isDigit() || it == '+' || it == ' ' || it == '-' }
                        },
                        label = { Text("Mobile number") },
                        placeholder = { Text("98765 43210") },
                        supportingText = { Text("Indian mobile number. Spaces, hyphens and +91 are accepted.") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !state.isLoading,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { submitOtpRequest() }),
                        shape = RoundedCornerShape(14.dp)
                    )

                    Button(
                        onClick = { submitOtpRequest() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        enabled = !state.isLoading && phone.isNotBlank(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                if (creatingAccount) "Send OTP and create account" else "Send OTP",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }

                    TextButton(
                        onClick = {
                            chooseMode(
                                if (creatingAccount) AuthMode.SIGN_IN else AuthMode.SIGN_UP
                            )
                        },
                        enabled = !state.isLoading
                    ) {
                        Text(
                            if (creatingAccount) {
                                "Already have an account? Sign in"
                            } else {
                                "New customer? Create an account"
                            }
                        )
                    }
                }

                else -> {
                    Text(
                        "Enter the OTP",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Enter the 6-digit code sent to ${state.phone}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = otp,
                        onValueChange = { input -> otp = input.filter(Char::isDigit).take(6) },
                        label = { Text("One-time password") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !state.isLoading,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboard?.hide()
                                if (otp.length == 6) viewModel.verifyOtp(otp)
                            }
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )

                    Button(
                        onClick = {
                            keyboard?.hide()
                            viewModel.verifyOtp(otp)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        enabled = !state.isLoading && otp.length == 6,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                if (creatingAccount) "Verify and continue" else "Verify and sign in",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                viewModel.changePhone()
                                phone = state.phone
                            },
                            enabled = !state.isLoading
                        ) {
                            Text("Change number")
                        }
                        TextButton(
                            onClick = {
                                otp = ""
                                viewModel.sendOtp(state.phone, createUser = creatingAccount)
                            },
                            enabled = !state.isLoading && state.resendSeconds == 0
                        ) {
                            Text(
                                if (state.resendSeconds > 0) {
                                    "Resend in ${state.resendSeconds}s"
                                } else {
                                    "Resend OTP"
                                }
                            )
                        }
                    }
                }
            }

            state.error?.let {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
            state.notice?.let {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "Signing in requires an existing account. New customers can create one with a mobile number. Guest browsing is not available.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { legalPage = LegalPage.PRIVACY }) {
                    Text("Privacy policy")
                }
                TextButton(onClick = { legalPage = LegalPage.TERMS }) {
                    Text("Terms of use")
                }
            }
        }
    }
}
