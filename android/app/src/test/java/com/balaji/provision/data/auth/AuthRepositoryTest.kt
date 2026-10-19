package com.balaji.provision.data.auth

import com.balaji.provision.data.network.AuthApi
import com.balaji.provision.data.network.AuthTokenRequest
import com.balaji.provision.data.network.AuthTokenResponse
import com.balaji.provision.data.network.AuthUser
import com.balaji.provision.data.network.RefreshTokenRequest
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import retrofit2.Response

class AuthRepositoryTest {

    private val api: AuthApi = mock(AuthApi::class.java)
    private val storage = FakeSessionStorage()
    private val repository = AuthRepository(api, storage)

    @Test
    fun `successful sign in persists session`() = runTest {
        `when`(api.signIn(anyString(), anyString(), anyString(), any())).thenReturn(
            Response.success(tokenResponse("access-1", "refresh-1"))
        )

        val result = repository.signIn("customer@example.com", "secret123")

        assertTrue(result is AuthResult.Success)
        assertEquals("access-1", storage.load()?.accessToken)
        assertEquals("customer@example.com", repository.session.value?.email)
    }

    @Test
    fun `failed sign in surfaces server message and keeps session empty`() = runTest {
        val body = """{"error":"invalid_grant","error_description":"Invalid login credentials"}"""
            .toResponseBody("application/json".toMediaType())
        `when`(api.signIn(anyString(), anyString(), anyString(), any())).thenReturn(Response.error(400, body))

        val result = repository.signIn("customer@example.com", "wrong")

        assertTrue(result is AuthResult.Failure)
        assertEquals("Invalid login credentials", (result as AuthResult.Failure).message)
        assertNull(repository.session.value)
        assertNull(storage.load())
    }

    @Test
    fun `expired token refreshes before use`() = runTest {
        storage.save(
            AuthSession(
                userId = "user-1",
                email = "customer@example.com",
                accessToken = "old-access",
                refreshToken = "old-refresh",
                expiresAtMillis = System.currentTimeMillis() - 1_000L
            )
        )
        val repository = AuthRepository(api, storage)
        `when`(api.refresh(anyString(), anyString(), anyString(), any())).thenReturn(
            Response.success(tokenResponse("new-access", "new-refresh"))
        )

        val token = repository.authorizedToken()

        assertEquals("new-access", token)
        assertEquals("new-refresh", storage.load()?.refreshToken)
    }

    @Test
    fun `failed refresh clears stored session`() = runTest {
        storage.save(
            AuthSession(
                userId = "user-1",
                email = "customer@example.com",
                accessToken = "old-access",
                refreshToken = "old-refresh",
                expiresAtMillis = System.currentTimeMillis() - 1_000L
            )
        )
        val repository = AuthRepository(api, storage)
        val body = """{"error":"invalid_grant"}""".toResponseBody("application/json".toMediaType())
        `when`(api.refresh(anyString(), anyString(), anyString(), any())).thenReturn(Response.error(401, body))

        val token = repository.authorizedToken()

        assertNull(token)
        assertNull(repository.session.value)
        assertNull(storage.load())
    }

    @Test
    fun `sign out calls logout and clears session`() = runTest {
        storage.save(
            AuthSession(
                userId = "user-1",
                email = "customer@example.com",
                accessToken = "access-1",
                refreshToken = "refresh-1",
                expiresAtMillis = System.currentTimeMillis() + 60_000L
            )
        )
        val repository = AuthRepository(api, storage)
        assertNotNull(repository.session.value)
        `when`(api.logout(anyString(), anyString())).thenReturn(Response.success(Unit))

        repository.signOut()

        assertNull(repository.session.value)
        assertNull(storage.load())
    }

    private fun tokenResponse(access: String, refresh: String) = AuthTokenResponse(
        access_token = access,
        refresh_token = refresh,
        expires_in = 3600L,
        user = AuthUser(id = "user-1", email = "customer@example.com")
    )

    private class FakeSessionStorage : SessionStorage {
        private var session: AuthSession? = null
        override fun load(): AuthSession? = session
        override fun save(session: AuthSession) {
            this.session = session
        }
        override fun clear() {
            session = null
        }
    }
}
