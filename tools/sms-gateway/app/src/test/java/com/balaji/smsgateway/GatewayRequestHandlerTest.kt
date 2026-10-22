package com.balaji.smsgateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayRequestHandlerTest {

    private val validBody =
        """{"phone_number":"+919876543210","message":"Balaji Provision Store OTP: 123456. Do not share this code.","request_id":"req-1"}"""

    @Test
    fun `valid request sends the sms once and returns success`() {
        val transport = FakeSmsTransport()
        val handler = handler(transport)

        val response = handler.handle("POST", "/api/send", "test-key", validBody)

        assertEquals(200, response.status)
        assertEquals("""{"success":true}""", response.body)
        assertEquals(1, transport.calls.size)
        assertEquals("+919876543210", transport.calls.single().first)
        assertTrue(transport.calls.single().second.contains("123456"))
    }

    @Test
    fun `wrong api key is rejected without sending`() {
        val transport = FakeSmsTransport()
        val handler = handler(transport)

        val response = handler.handle("POST", "/api/send", "wrong-key", validBody)

        assertEquals(401, response.status)
        assertTrue(transport.calls.isEmpty())
        assertFalse(response.body.contains("123456"))
        assertFalse(response.body.contains("+919876543210"))
    }

    @Test
    fun `invalid body does not send or consume rate limit`() {
        val transport = FakeSmsTransport()
        val handler = handler(transport, perMinute = 1)

        val invalid = handler.handle("POST", "/api/send", "test-key", """{"phone_number":"bad"}""")
        val firstValid = handler.handle("POST", "/api/send", "test-key", validBody)
        val secondValid = handler.handle("POST", "/api/send", "test-key", validBody)

        assertEquals(400, invalid.status)
        assertEquals(200, firstValid.status)
        assertEquals(429, secondValid.status)
        assertEquals(1, transport.calls.size)
    }

    @Test
    fun `rate limit rejects before sending`() {
        val transport = FakeSmsTransport()
        val handler = handler(transport, perMinute = 1)

        val first = handler.handle("POST", "/api/send", "test-key", validBody)
        val second = handler.handle("POST", "/api/send", "test-key", validBody)

        assertEquals(200, first.status)
        assertEquals(429, second.status)
        assertEquals(1, transport.calls.size)
    }

    @Test
    fun `sms transport failure returns generic bad gateway`() {
        val transport = FakeSmsTransport(SmsSendResult.Failed("radio_failure"))
        val handler = handler(transport)

        val response = handler.handle("POST", "/api/send", "test-key", validBody)

        assertEquals(502, response.status)
        assertEquals("""{"success":false,"error":"send_failed"}""", response.body)
    }

    @Test
    fun `rejects wrong method and path`() {
        val transport = FakeSmsTransport()
        val handler = handler(transport)

        assertEquals(405, handler.handle("GET", "/api/send", "test-key", null).status)
        assertEquals(404, handler.handle("POST", "/other", "test-key", validBody).status)
        assertTrue(transport.calls.isEmpty())
    }

    private fun handler(
        transport: SmsTransport,
        perMinute: Int = 10,
        perHour: Int = 100,
    ): GatewayRequestHandler = GatewayRequestHandler(
        apiKeyProvider = { "test-key" },
        rateLimiter = SlidingWindowRateLimiter(perMinute, perHour),
        smsTransport = transport,
    )

    private class FakeSmsTransport(
        private val result: SmsSendResult = SmsSendResult.Sent,
    ) : SmsTransport {
        val calls = mutableListOf<Pair<String, String>>()

        override fun send(phoneNumber: String, message: String): SmsSendResult {
            calls += phoneNumber to message
            return result
        }
    }
}
