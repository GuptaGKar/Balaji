package com.balaji.provision.data.repository

import com.balaji.provision.data.auth.AuthRepository
import com.balaji.provision.data.auth.AuthSession
import com.balaji.provision.data.network.AddressResponse
import com.balaji.provision.data.network.ProfileResponse
import com.balaji.provision.data.network.SaveMyAddressRequest
import com.balaji.provision.data.network.SaveMyAddressResponse
import com.balaji.provision.data.network.SupabaseApi
import com.balaji.provision.domain.model.AddressDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import retrofit2.Response

class AccountRepositoryTest {

    private val api: SupabaseApi = mock(SupabaseApi::class.java)
    private val auth: AuthRepository = mock(AuthRepository::class.java)
    private val repository = AccountRepositoryImpl(api, auth)

    @Test
    fun `load profile returns the signed in customer`() = runTest {
        signIn()
        `when`(api.getProfile(anyString(), anyString(), eq("eq.user-1"), anyString()))
            .thenReturn(Response.success(listOf(ProfileResponse(display_name = "Asha", phone = "9876543210", role = "customer"))))

        val result = repository.loadProfile()

        assertTrue(result is AccountResult.Success)
        assertEquals("Asha", (result as AccountResult.Success).value.displayName)
        assertEquals("9876543210", result.value.phone)
    }

    @Test
    fun `save address sends normalized values and returns the saved address`() = runTest {
        signIn()
        val requestCaptor = argumentCaptor<SaveMyAddressRequest>()
        `when`(api.saveMyAddress(anyString(), anyString(), anyString(), any()))
            .thenAnswer {
                Response.success(
                    SaveMyAddressResponse(
                        address = AddressResponse(
                            id = "address-1",
                            customer_id = "user-1",
                            label = "Home",
                            recipient_name = "Asha",
                            phone = "9876543210",
                            address_lines = "12, MG Road",
                            landmark = "Near temple",
                            area = "Shivaji Nagar",
                            city = "Pune",
                            state = "Maharashtra",
                            postal_code = "411005",
                            is_default = true
                        )
                    )
                )
            }

        val result = repository.saveAddress(validDraft().copy(phone = "+91 98765-43210"))

        assertTrue(result is AccountResult.Success)
        assertEquals("address-1", (result as AccountResult.Success).value.id)
        verify(api).saveMyAddress(anyString(), anyString(), anyString(), requestCaptor.capture())
        assertEquals("9876543210", requestCaptor.firstValue.p_phone)
        assertEquals("411005", requestCaptor.firstValue.p_postal_code)
        assertEquals(true, requestCaptor.firstValue.p_is_default)
    }

    @Test
    fun `load addresses maps the default flag`() = runTest {
        signIn()
        `when`(api.getAddresses(anyString(), anyString(), eq("eq.user-1"), anyString(), anyString()))
            .thenReturn(
                Response.success(
                    listOf(
                        AddressResponse(
                            id = "address-1",
                            customer_id = "user-1",
                            label = "Home",
                            recipient_name = "Asha",
                            phone = "9876543210",
                            address_lines = "12, MG Road",
                            city = "Pune",
                            state = "Maharashtra",
                            postal_code = "411005",
                            is_default = true
                        )
                    )
                )
            )

        val result = repository.loadAddresses()

        assertTrue(result is AccountResult.Success)
        assertEquals(true, (result as AccountResult.Success).value.single().isDefault)
    }

    @Test
    fun `save address surfaces server validation message`() = runTest {
        signIn()
        val body = """{"error":"VALIDATION_FAILED","message":"Enter a valid 6-digit PIN code"}"""
            .toResponseBody("application/json".toMediaType())
        `when`(api.saveMyAddress(anyString(), anyString(), anyString(), any()))
            .thenReturn(Response.error(400, body))

        val result = repository.saveAddress(validDraft())

        assertEquals("Enter a valid 6-digit PIN code", (result as AccountResult.Failure).message)
    }

    @Test
    fun `address operations require a signed in session`() = runTest {
        whenever(auth.session).thenReturn(MutableStateFlow(null))
        whenever(auth.authorizedToken()).thenReturn(null)

        val result = repository.loadAddresses()

        assertTrue(result is AccountResult.Failure)
        verifyNoInteractions(api)
    }

    private suspend fun signIn() {
        whenever(auth.session).thenReturn(
            MutableStateFlow(
                AuthSession(
                    userId = "user-1",
                    accessToken = "access",
                    refreshToken = "refresh",
                    expiresAtMillis = Long.MAX_VALUE
                )
            )
        )
        whenever(auth.authorizedToken()).thenReturn("access")
    }

    private fun validDraft() = AddressDraft(
        label = "Home",
        recipientName = "Asha",
        phone = "9876543210",
        addressLines = "12, MG Road",
        landmark = "Near temple",
        area = "Shivaji Nagar",
        city = "Pune",
        state = "Maharashtra",
        postalCode = "411005",
        isDefault = true
    )
}
