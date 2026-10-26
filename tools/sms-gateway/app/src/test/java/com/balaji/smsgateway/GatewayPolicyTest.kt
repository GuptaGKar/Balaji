package com.balaji.smsgateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayPolicyTest {

    @Test
    fun `accepts valid Indian mobile and six digit message`() {
        val request = GatewayPolicy.parseSendRequest(
            """
            {"phone_number":"+919876543210","message":"Balaji Provision Store OTP: 123456. Do not share this code.","request_id":"req-1"}
            """.trimIndent(),
        )

        assertNotNull(request)
        assertEquals("+919876543210", request?.phoneNumber)
        assertEquals("Balaji Provision Store OTP: 123456. Do not share this code.", request?.message)
        assertEquals("req-1", request?.requestId)
    }

    @Test
    fun `rejects malformed or non Indian numbers`() {
        val invalidNumbers = listOf(
            "919876543210",
            "09876543210",
            "+915876543210",
            "+91987654321",
            "+9198765432100",
            "+91 98765 43210",
            "",
        )

        invalidNumbers.forEach { phone ->
            val request = GatewayPolicy.parseSendRequest(
                """{"phone_number":"$phone","message":"Balaji OTP: 123456"}""",
            )
            assertNull("accepted $phone", request)
        }
    }

    @Test
    fun `rejects missing body empty message oversized message and malformed json`() {
        assertNull(GatewayPolicy.parseSendRequest(null))
        assertNull(GatewayPolicy.parseSendRequest(""))
        assertNull(GatewayPolicy.parseSendRequest("not json"))
        assertNull(GatewayPolicy.parseSendRequest("""{"phone_number":"+919876543210"}"""))
        assertNull(GatewayPolicy.parseSendRequest("""{"phone_number":"+919876543210","message":""}"""))
        assertNull(
            GatewayPolicy.parseSendRequest(
                """{"phone_number":"+919876543210","message":"123456${"x".repeat(315)}"}""",
            ),
        )
        assertNotNull(
            GatewayPolicy.parseSendRequest(
                """{"phone_number":"+919876543210","message":"123456${"x".repeat(314)}"}""",
            ),
        )
    }

    @Test
    fun `rejects body without a six digit otp`() {
        assertNull(
            GatewayPolicy.parseSendRequest(
                """{"phone_number":"+919876543210","message":"Balaji Provision Store OTP: 12345. Do not share this code."}""",
            ),
        )
        assertNull(
            GatewayPolicy.parseSendRequest(
                """{"phone_number":"+919876543210","message":"Balaji Provision Store OTP: 1234567. Do not share this code."}""",
            ),
        )
        assertNotNull(
            GatewayPolicy.parseSendRequest(
                """{"phone_number":"+919876543210","message":"Balaji Provision Store OTP: 123456. Do not share this code."}""",
            ),
        )
    }

    @Test
    fun `api keys must be at least 32 characters after trimming`() {
        assertFalse(GatewayPolicy.isValidApiKey(null))
        assertFalse(GatewayPolicy.isValidApiKey(""))
        assertFalse(GatewayPolicy.isValidApiKey("x".repeat(31)))
        assertTrue(GatewayPolicy.isValidApiKey("x".repeat(32)))
        assertTrue(GatewayPolicy.isValidApiKey("  ${"x".repeat(32)}  "))
    }

    @Test
    fun `constant time comparison accepts only an exact non empty key`() {
        assertTrue(GatewayPolicy.apiKeyMatches("secret-key-123", "secret-key-123"))
        assertFalse(GatewayPolicy.apiKeyMatches("secret-key-123", "secret-key-124"))
        assertFalse(GatewayPolicy.apiKeyMatches("secret-key-123", "secret-key"))
        assertFalse(GatewayPolicy.apiKeyMatches("secret-key-123", null))
        assertFalse(GatewayPolicy.apiKeyMatches("", ""))
        assertFalse(GatewayPolicy.apiKeyMatches(null, "secret-key-123"))
    }
}
