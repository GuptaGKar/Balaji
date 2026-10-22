package com.balaji.provision.data.network

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthRequestTest {

    @Test
    fun `otp request explicitly creates a user`() {
        val request = OtpRequest(phone = "+919876543210", create_user = true)

        assertEquals(
            """{"phone":"+919876543210","create_user":true}""",
            Json.encodeToString(request)
        )
    }

    @Test
    fun `verify request includes the sms verification type`() {
        val request = VerifyOtpRequest(
            phone = "+919876543210",
            token = "123456",
            type = "sms"
        )

        assertEquals(
            """{"phone":"+919876543210","token":"123456","type":"sms"}""",
            Json.encodeToString(request)
        )
    }
}
