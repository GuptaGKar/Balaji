package com.balaji.provision.data.auth

import com.balaji.provision.data.network.AuthApi
import com.balaji.provision.data.network.AuthTokenResponse
import com.balaji.provision.data.network.AuthUser
import com.balaji.provision.data.network.OtpRequest
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
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import retrofit2.Response

class AuthRepositoryTest {

    private val api: AuthApi = mock(AuthApi::class.java)
    private val storage = FakeSessionStorage()
    private val repository = AuthRepository(api, storage)

    @Test
    fun `request otp normalizes indian number`() = runTest {
        `when`(api.sendOtp(anyString(), anyString(), any<OtpRequest>()))
            .thenReturn(Response.success(Unit))

        val result = repository.requestOtp("+91 98765-43210")

        assertEquals(OtpResult.Sent, result)
        verify(api).sendOtp(anyString(), anyString(), eq(OtpRequest(phone = "+919876543210")))
    }

    @Test
    fun `request otp rejects invalid number without calling api`() = runTest {
        val result = repository.requestOtp("12345")

        assertTrue(result is OtpResult.Failure)
        verifyNoInteractions(api)
    }

    @Test
    fun `request otp surfaces server message`() = runTest {
        val body = """{"msg":"For security purposes, you can only request this after 60 seconds."}"""
            .toResponseBody("application/json".toMediaType())
        `when`(api.sendOtp(anyString(), anyString(), any<OtpRequest>()))
            .thenReturn(Response.error(429, body))

        val result = repository.requestOtp("9876543210")

        assertEquals(
            "For security purposes, you can only request this after 60 seconds.",
            (result as OtpResult.Failure).message
        )
    }

    @Test
    fun `verified otp persists phone session`() = runTest {
        `when`(api.verifyOtp(anyString(), anyString(), any()))
            .thenReturn(Response.success(tokenResponse("access-1", "refresh-1")))

        val result = repository.verifyOtp("9876543210", "123 456")

        assertTrue(result is AuthResult.Success)
        assertEquals("access-1", storage.load()?.accessToken)
        assertEquals("+919876543210", repository.session.value?.phone)
        assertNull(repository.session.value?.email)
    }

    @Test
    fun `verified otp loads user when token response omits it`() = runTest {
        `when`(api.verifyOtp(anyString(), anyString(), any()))
            .thenReturn(Response.success(tokenResponse("access-1", "refresh-1", withUser = false)))
        `when`(api.getUser(anyString(), anyString()))
            .thenReturn(Response.success(AuthUser(id = "user-2", phone = "+919876543210")))

        val result = repository.verifyOtp("9876543210", "123456")

        assertTrue(result is AuthResult.Success)
        assertEquals("user-2", repository.session.value?.userId)
        assertEquals("+919876543210", repository.session.value?.phone)
    }

    @Test
    fun `verified otp fails when user cannot be loaded`() = runTest {
        `when`(api.verifyOtp(anyString(), anyString(), any()))
            .thenReturn(Response.success(tokenResponse("access-1", "refresh-1", withUser = false)))
        val body = """{"msg":"User not found"}""".toResponseBody("application/json".toMediaType())
        `when`(api.getUser(anyString(), anyString())).thenReturn(Response.error(401, body))

        val result = repository.verifyOtp("9876543210", "123456")

        assertTrue(result is AuthResult.Failure)
        assertNull(repository.session.value)
        assertNull(storage.load())
    }

    @Test
    fun `invalid otp length does not call api`() = runTest {
        val result = repository.verifyOtp("9876543210", "123")

        assertTrue(result is AuthResult.Failure)
        verifyNoInteractions(api)
    }

    @Test
    fun `failed otp verification surfaces server message`() = runTest {
        val body = """{"msg":"Token has expired or is invalid"}"""
            .toResponseBody("application/json".toMediaType())
        `when`(api.verifyOtp(anyString(), anyString(), any()))
            .thenReturn(Response.error(403, body))

        val result = repository.verifyOtp("9876543210", "000000")

        assertEquals("Token has expired or is invalid", (result as AuthResult.Failure).message)
        assertNull(repository.session.value)
        assertNull(storage.load())
    }

    @Test
    fun `expired token refreshes before use and keeps phone`() = runTest {
        storage.save(
            AuthSession(
                userId = "user-1",
                accessToken = "old-access",
                refreshToken = "old-refresh",
                expiresAtMillis = System.currentTimeMillis() - 1_000L,
                phone = "+919876543210"
            )
        )
        val repository = AuthRepository(api, storage)
        `when`(api.refresh(anyString(), anyString(), anyString(), any()))
            .thenReturn(Response.success(tokenResponse("new-access", "new-refresh", phone = null)))

        val token = repository.authorizedToken()

        assertEquals("new-access", token)
        assertEquals("new-refresh", storage.load()?.refreshToken)
        assertEquals("+919876543210", storage.load()?.phone)
    }

    @Test
    fun `failed refresh clears stored session`() = runTest {
        storage.save(
            AuthSession(
                userId = "user-1",
                accessToken = "old-access",
                refreshToken = "old-refresh",
                expiresAtMillis = System.currentTimeMillis() - 1_000L,
                phone = "+919876543210"
            )
        )
        val repository = AuthRepository(api, storage)
        val body = """{"error":"invalid_grant"}""".toResponseBody("application/json".toMediaType())
        `when`(api.refresh(anyString(), anyString(), anyString(), any()))
            .thenReturn(Response.error(401, body))

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
                accessToken = "access-1",
                refreshToken = "refresh-1",
                expiresAtMillis = System.currentTimeMillis() + 60_000L,
                phone = "+919876543210"
            )
        )
        val repository = AuthRepository(api, storage)
        assertNotNull(repository.session.value)
        `when`(api.logout(anyString(), anyString())).thenReturn(Response.success(Unit))

        repository.signOut()

        assertNull(repository.session.value)
        assertNull(storage.load())
    }

    private fun tokenResponse(
        access: String,
        refresh: String,
        phone: String? = "+919876543210",
        withUser: Boolean = true
    ) = AuthTokenResponse(
        access_token = access,
        refresh_token = refresh,
        expires_in = 3600L,
        user = if (withUser) AuthUser(id = "user-1", phone = phone) else null
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
