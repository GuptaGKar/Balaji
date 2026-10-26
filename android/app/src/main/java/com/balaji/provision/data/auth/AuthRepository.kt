package com.balaji.provision.data.auth

import com.balaji.provision.BuildConfig
import com.balaji.provision.data.network.AuthApi
import com.balaji.provision.data.network.AuthErrorResponse
import com.balaji.provision.data.network.AuthTokenResponse
import com.balaji.provision.data.network.AuthUser
import com.balaji.provision.data.network.OtpRequest
import com.balaji.provision.data.network.PasswordGrantRequest
import com.balaji.provision.data.network.RefreshTokenRequest
import com.balaji.provision.data.network.UpdatePasswordRequest
import com.balaji.provision.data.network.VerifyOtpRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AuthResult {
    data class Success(val session: AuthSession) : AuthResult
    data class Failure(val message: String) : AuthResult
}

sealed interface PasswordChangeResult {
    data object Success : PasswordChangeResult
    data class Failure(val message: String) : PasswordChangeResult
}

sealed interface OtpResult {
    data object Sent : OtpResult
    data class Failure(val message: String) : OtpResult
}

@Singleton
class AuthRepository @Inject constructor(
    private val api: AuthApi,
    private val store: SessionStorage
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    private val _session = MutableStateFlow(store.load())
    val session: StateFlow<AuthSession?> = _session.asStateFlow()

    suspend fun requestOtp(phoneInput: String, createUser: Boolean = true): OtpResult {
        val phone = PhoneNumber.normalizeIndian(phoneInput)
            ?: return OtpResult.Failure("Enter a valid 10-digit Indian mobile number.")
        return try {
            val response = api.sendOtp(
                apiKey = BuildConfig.SUPABASE_ANON_KEY,
                body = OtpRequest(phone = phone, create_user = createUser)
            )
            if (response.isSuccessful) {
                OtpResult.Sent
            } else {
                OtpResult.Failure(
                    response.errorMessage(
                        fallback = "Could not send the OTP. Check your connection and try again.",
                        allowAccountCreation = createUser
                    )
                )
            }
        } catch (_: Exception) {
            OtpResult.Failure("Could not reach the store. Check your connection and try again.")
        }
    }

    suspend fun verifyOtp(phoneInput: String, tokenInput: String): AuthResult {
        val phone = PhoneNumber.normalizeIndian(phoneInput)
            ?: return AuthResult.Failure("Enter a valid 10-digit Indian mobile number.")
        val token = tokenInput.filter(Char::isDigit)
        if (token.length != 6) {
            return AuthResult.Failure("Enter the 6-digit code sent to your phone.")
        }
        return authenticate(
            fallback = "Could not verify the OTP. Check the code and try again."
        ) {
            api.verifyOtp(
                apiKey = BuildConfig.SUPABASE_ANON_KEY,
                body = VerifyOtpRequest(phone = phone, token = token, type = "sms")
            )
        }
    }

    suspend fun signInWithPassword(loginIdInput: String, password: String): AuthResult {
        val loginId = loginIdInput.trim()
        if (loginId.isBlank()) {
            return AuthResult.Failure("Enter your staff login ID.")
        }
        if (password.isBlank()) {
            return AuthResult.Failure("Enter your password.")
        }
        val email = if (loginId.contains('@')) {
            loginId
        } else {
            "${loginId.lowercase(Locale.ROOT)}@riders.invalid"
        }
        return authenticate(fallback = "Incorrect login ID or password.") {
            api.signInWithPassword(
                apiKey = BuildConfig.SUPABASE_ANON_KEY,
                body = PasswordGrantRequest(email = email, password = password)
            )
        }
    }

    suspend fun updatePassword(newPassword: String): PasswordChangeResult {
        if (newPassword.length < 8) {
            return PasswordChangeResult.Failure("Use at least 8 characters.")
        }
        val token = authorizedToken()
            ?: return PasswordChangeResult.Failure("Your session expired. Sign in again.")
        return try {
            val response = api.updatePassword(
                apiKey = BuildConfig.SUPABASE_ANON_KEY,
                authorization = "Bearer $token",
                body = UpdatePasswordRequest(password = newPassword)
            )
            if (!response.isSuccessful) {
                return PasswordChangeResult.Failure(
                    response.errorMessage("Could not update your password.")
                )
            }
            val flag = api.completePasswordChange(
                apiKey = BuildConfig.SUPABASE_ANON_KEY,
                authorization = "Bearer $token"
            )
            val body = flag.body()
            if (!flag.isSuccessful || body?.success != true) {
                return PasswordChangeResult.Failure(
                    body?.message
                        ?: "Password updated, but the account status could not be refreshed. Try again."
                )
            }
            PasswordChangeResult.Success
        } catch (_: Exception) {
            PasswordChangeResult.Failure(
                "Could not reach the store. Check your connection and try again."
            )
        }
    }

    suspend fun authorizedToken(): String? = mutex.withLock {
        val current = _session.value ?: return@withLock null
        if (current.expiresAtMillis > System.currentTimeMillis() + 60_000L) {
            return@withLock current.accessToken
        }

        val response = try {
            api.refresh(
                apiKey = BuildConfig.SUPABASE_ANON_KEY,
                body = RefreshTokenRequest(refresh_token = current.refreshToken)
            )
        } catch (_: Exception) {
            return@withLock current.accessToken.takeIf { current.expiresAtMillis > System.currentTimeMillis() }
        }

        val body = response.body()
        if (!response.isSuccessful || body?.access_token.isNullOrBlank() || body == null) {
            clearSession()
            return@withLock null
        }
        val refreshed = body.toSession(
            fallbackEmail = current.email,
            fallbackPhone = current.phone,
            fallbackUserId = current.userId
        )
        persist(refreshed)
        refreshed.accessToken
    }

    suspend fun signOut() {
        val current = _session.value
        clearSession()
        if (current != null) {
            try {
                api.logout(
                    apiKey = BuildConfig.SUPABASE_ANON_KEY,
                    authorization = "Bearer ${current.accessToken}"
                )
            } catch (_: Exception) {
                // Local session is already cleared; network logout is best-effort.
            }
        }
    }

    private suspend fun authenticate(
        fallback: String,
        call: suspend () -> retrofit2.Response<AuthTokenResponse>
    ): AuthResult {
        return try {
            val response = call()
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                return AuthResult.Failure(response.errorMessage(fallback))
            }
            if (body.access_token.isNullOrBlank() || body.refresh_token.isNullOrBlank()) {
                return AuthResult.Failure(fallback)
            }
            val user = if (body.user?.id.isNullOrBlank()) {
                val userResponse = try {
                    api.getUser(
                        apiKey = BuildConfig.SUPABASE_ANON_KEY,
                        authorization = "Bearer ${body.access_token}"
                    )
                } catch (_: Exception) {
                    return AuthResult.Failure(
                        "Signed in, but your account could not be loaded. Check your connection and try again."
                    )
                }
                if (!userResponse.isSuccessful || userResponse.body()?.id.isNullOrBlank()) {
                    return AuthResult.Failure(
                        "Signed in, but your account could not be loaded. Try again."
                    )
                }
                userResponse.body()
            } else {
                body.user
            }
            val session = body.toSession(user = user)
            persist(session)
            AuthResult.Success(session)
        } catch (_: Exception) {
            AuthResult.Failure("Could not reach the store. Check your connection and try again.")
        }
    }

    private fun AuthTokenResponse.toSession(
        user: AuthUser? = this.user,
        fallbackEmail: String? = null,
        fallbackPhone: String? = null,
        fallbackUserId: String? = null
    ): AuthSession {
        val expiresIn = expires_in ?: 3600L
        return AuthSession(
            userId = user?.id ?: fallbackUserId.orEmpty(),
            accessToken = access_token.orEmpty(),
            refreshToken = refresh_token.orEmpty(),
            expiresAtMillis = System.currentTimeMillis() + expiresIn * 1000L,
            email = user?.email ?: fallbackEmail,
            phone = user?.phone ?: fallbackPhone
        )
    }

    private fun persist(session: AuthSession) {
        store.save(session)
        _session.value = session
    }

    private fun clearSession() {
        store.clear()
        _session.value = null
    }

    private fun retrofit2.Response<*>.errorMessage(
        fallback: String,
        allowAccountCreation: Boolean = true
    ): String {
        val raw = errorBody()?.string().orEmpty()
        val parsed = runCatching { json.decodeFromString<AuthErrorResponse>(raw) }.getOrNull()
        val serverMessage = parsed?.error_description
            ?: parsed?.msg
            ?: parsed?.message
        if (parsed?.error_code == "phone_provider_disabled" ||
            serverMessage.equals("Unsupported phone provider", ignoreCase = true)
        ) {
            return "Phone sign-in isn't enabled yet. Please contact the store."
        }
        val noAccount = parsed?.error_code?.let {
            it in setOf("otp_disabled", "signup_disabled", "user_not_found")
        } == true ||
            serverMessage?.contains("signups not allowed", ignoreCase = true) == true ||
            serverMessage?.contains("user not found", ignoreCase = true) == true
        if (!allowAccountCreation && noAccount) {
            return "No account found for this number. Create an account first."
        }
        return serverMessage ?: fallback
    }
}
