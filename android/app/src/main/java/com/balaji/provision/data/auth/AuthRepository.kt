package com.balaji.provision.data.auth

import com.balaji.provision.BuildConfig
import com.balaji.provision.data.network.AuthApi
import com.balaji.provision.data.network.AuthErrorResponse
import com.balaji.provision.data.network.AuthTokenRequest
import com.balaji.provision.data.network.AuthTokenResponse
import com.balaji.provision.data.network.RefreshTokenRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AuthResult {
    data class Success(val session: AuthSession?) : AuthResult
    data class Failure(val message: String) : AuthResult
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

    suspend fun signIn(email: String, password: String): AuthResult = authenticate {
        api.signIn(
            apiKey = BuildConfig.SUPABASE_ANON_KEY,
            body = AuthTokenRequest(email = email.trim(), password = password)
        )
    }

    suspend fun signUp(email: String, password: String): AuthResult = authenticate {
        api.signUp(
            apiKey = BuildConfig.SUPABASE_ANON_KEY,
            body = AuthTokenRequest(email = email.trim(), password = password)
        )
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
        } catch (e: Exception) {
            return@withLock current.accessToken.takeIf { current.expiresAtMillis > System.currentTimeMillis() }
        }

        val body = response.body()
        if (!response.isSuccessful || body?.access_token.isNullOrBlank() || body == null) {
            clearSession()
            return@withLock null
        }
        val refreshed = body!!.toSession(fallbackEmail = current.email, fallbackUserId = current.userId)
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

    private suspend fun authenticate(call: suspend () -> retrofit2.Response<AuthTokenResponse>): AuthResult {
        return try {
            val response = call()
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                return AuthResult.Failure(response.errorMessage())
            }
            if (body.access_token.isNullOrBlank() || body.refresh_token.isNullOrBlank()) {
                return AuthResult.Success(null)
            }
            val session = body.toSession()
            persist(session)
            AuthResult.Success(session)
        } catch (e: Exception) {
            AuthResult.Failure("Could not reach the store. Check your connection and try again.")
        }
    }

    private fun AuthTokenResponse.toSession(
        fallbackEmail: String? = null,
        fallbackUserId: String? = null
    ): AuthSession {
        val expiresIn = expires_in ?: 3600L
        return AuthSession(
            userId = user?.id ?: fallbackUserId.orEmpty(),
            email = user?.email ?: fallbackEmail.orEmpty(),
            accessToken = access_token.orEmpty(),
            refreshToken = refresh_token.orEmpty(),
            expiresAtMillis = System.currentTimeMillis() + expiresIn * 1000L
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

    private fun retrofit2.Response<AuthTokenResponse>.errorMessage(): String {
        val raw = errorBody()?.string().orEmpty()
        val parsed = runCatching { json.decodeFromString<AuthErrorResponse>(raw) }.getOrNull()
        return parsed?.error_description
            ?: parsed?.msg
            ?: parsed?.message
            ?: "Sign-in failed. Check your email and password."
    }
}
