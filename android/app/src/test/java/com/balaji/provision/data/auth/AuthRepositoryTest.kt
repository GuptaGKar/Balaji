package com.balaji.provision.data.auth

import com.balaji.provision.data.network.AuthApi
import com.balaji.provision.data.network.AuthTokenResponse
import com.balaji.provision.data.network.AuthUser
import com.balaji.provision.data.network.CompletePasswordChangeResponse
import com.balaji.provision.data.network.OtpRequest
import com.balaji.provision.data.network.PasswordGrantRequest
import com.balaji.provision.data.network.UpdatePasswordRequest
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
        verify(api).sendOtp(anyString(), anyString(), eq(OtpRequest(phone = "+919876543210", create_user = true)))
    }

    @Test
    fun `sign in otp does not create an account`() = runTest {
        `when`(api.sendOtp(anyString(), anyString(), any<OtpRequest>()))
            .thenReturn(Response.success(Unit))

        val result = repository.requestOtp("9876543210", createUser = false)

        assertEquals(OtpResult.Sent, result)
        verify(api).sendOtp(
            anyString(),
            anyString(),
            eq(OtpRequest(phone = "+919876543210", create_user = false))
        )
    }

    @Test
    fun `sign in otp explains when no account exists`() = runTest {
        val body = """{"error_code":"otp_disabled","msg":"Signups not allowed for otp"}"""
            .toResponseBody("application/json".toMediaType())
        `when`(api.sendOtp(anyString(), anyString(), any<OtpRequest>()))
            .thenReturn(Response.error(422, body))

        val result = repository.requestOtp("9876543210", createUser = false)

        assertEquals(
            "No account found for this number. Create an account first.",
            (result as OtpResult.Failure).message
        )
    }

    @Test
    fun `request otp rejects invalid number without calling api`() = runTest {
        val result = repository.requestOtp("12345")

        assertTrue(result is OtpResult.Failure)
        verifyNoInteractions(api)
    }

    @Test
    fun `request otp explains disabled phone provider`() = runTest {
        val body = """{"code":400,"error_code":"phone_provider_disabled","msg":"Unsupported phone provider"}"""
            .toResponseBody("application/json".toMediaType())
        `when`(api.sendOtp(anyString(), anyString(), any<OtpRequest>()))
            .thenReturn(Response.error(400, body))

        val result = repository.requestOtp("9876543210")

        assertEquals(
            "Phone sign-in isn't enabled yet. Please contact the store.",
            (result as OtpResult.Failure).message
        )
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
    fun `password login maps rider login id to synthetic email`() = runTest {
        `when`(api.signInWithPassword(anyString(), anyString(), anyString(), any()))
            .thenReturn(Response.success(tokenResponse("access-r", "refresh-r", email = "rider1@riders.invalid")))

        val result = repository.signInWithPassword(" Rider1 ", "temp-password")

        assertTrue(result is AuthResult.Success)
        verify(api).signInWithPassword(
            anyString(),
            anyString(),
            anyString(),
            eq(PasswordGrantRequest("rider1@riders.invalid", "temp-password"))
        )
        assertEquals("access-r", storage.load()?.accessToken)
        assertEquals("rider1@riders.invalid", repository.session.value?.email)
    }

    @Test
    fun `password login keeps a full email unchanged`() = runTest {
        `when`(api.signInWithPassword(anyString(), anyString(), anyString(), any()))
            .thenReturn(Response.success(tokenResponse("access-r", "refresh-r", email = "boss@example.com")))

        repository.signInWithPassword("boss@example.com", "temp-password")

        verify(api).signInWithPassword(
            anyString(),
            anyString(),
            anyString(),
            eq(PasswordGrantRequest("boss@example.com", "temp-password"))
        )
    }

    @Test
    fun `password login requires both fields without calling api`() = runTest {
        assertTrue(repository.signInWithPassword("", "secret") is AuthResult.Failure)
        assertTrue(repository.signInWithPassword("rider1", "  ") is AuthResult.Failure)
        verifyNoInteractions(api)
    }

    @Test
    fun `password login surfaces invalid credentials message`() = runTest {
        val body = """{"error_code":"invalid_credentials","msg":"Invalid login credentials"}"""
            .toResponseBody("application/json".toMediaType())
        `when`(api.signInWithPassword(anyString(), anyString(), anyString(), any()))
            .thenReturn(Response.error(400, body))

        val result = repository.signInWithPassword("rider1", "wrong")

        assertEquals("Invalid login credentials", (result as AuthResult.Failure).message)
        assertNull(storage.load())
    }

    @Test
    fun `update password clears the change flag`() = runTest {
        storage.save(
            AuthSession(
                userId = "user-1",
                accessToken = "access-1",
                refreshToken = "refresh-1",
                expiresAtMillis = System.currentTimeMillis() + 3_600_000L,
                email = "rider1@riders.invalid"
            )
        )
        val repository = AuthRepository(api, storage)
        `when`(api.updatePassword(anyString(), anyString(), anyString(), any()))
            .thenReturn(Response.success(AuthUser(id = "user-1", email = "rider1@riders.invalid")))
        `when`(api.completePasswordChange(anyString(), anyString(), anyString(), any()))
            .thenReturn(Response.success(CompletePasswordChangeResponse(success = true)))

        val result = repository.updatePassword("new-password")

        assertEquals(PasswordChangeResult.Success, result)
        verify(api).updatePassword(
            anyString(),
            eq("Bearer access-1"),
            anyString(),
            eq(UpdatePasswordRequest("new-password"))
        )
    }

    @Test
    fun `update password rejects short password`() = runTest {
        val result = repository.updatePassword("short")

        assertEquals(
            "Use at least 8 characters.",
            (result as PasswordChangeResult.Failure).message
        )
        verifyNoInteractions(api)
    }

    @Test
    fun `update password fails when the flag cannot be cleared`() = runTest {
        storage.save(
            AuthSession(
                userId = "user-1",
                accessToken = "access-1",
                refreshToken = "refresh-1",
                expiresAtMillis = System.currentTimeMillis() + 3_600_000L
            )
        )
        val repository = AuthRepository(api, storage)
        `when`(api.updatePassword(anyString(), anyString(), anyString(), any()))
            .thenReturn(Response.success(AuthUser(id = "user-1")))
        `when`(api.completePasswordChange(anyString(), anyString(), anyString(), any()))
            .thenReturn(Response.success(CompletePasswordChangeResponse(error = "FORBIDDEN", message = "Active profile required")))

        val result = repository.updatePassword("new-password")

        assertEquals(
            "Active profile required",
            (result as PasswordChangeResult.Failure).message
        )
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
        withUser: Boolean = true,
        email: String? = null
    ) = AuthTokenResponse(
        access_token = access,
        refresh_token = refresh,
        expires_in = 3600L,
        user = if (withUser) AuthUser(id = "user-1", phone = phone, email = email) else null
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
