package com.balaji.smsgateway

import java.net.HttpURLConnection
import java.net.URL
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GatewayServerIntegrationTest {

    private val transport = FakeSmsTransport()
    private lateinit var server: GatewayServer
    private var port = 0

    @Before
    fun setUp() {
        server = GatewayServer(
            0,
            GatewayRequestHandler(
                apiKeyProvider = { "test-key" },
                rateLimiter = SlidingWindowRateLimiter(perMinute = 10, perHour = 100),
                smsTransport = transport,
            ),
        )
        server.start(5_000, false)
        port = server.listeningPort
    }

    @After
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `http request with valid key sends sms and returns success`() {
        val (status, body) = post(validBody(), "test-key")

        assertEquals(200, status)
        assertEquals("""{"success":true}""", body)
        assertEquals(1, transport.calls.size)
    }

    @Test
    fun `http request without key is unauthorized and does not send`() {
        val (status, _) = post(validBody(), null)

        assertEquals(401, status)
        assertTrue(transport.calls.isEmpty())
    }

    @Test
    fun `http get is rejected without sending`() {
        val connection = URL("http://127.0.0.1:$port/api/send").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"

        val status = connection.responseCode
        connection.disconnect()

        assertEquals(405, status)
        assertTrue(transport.calls.isEmpty())
    }

    private fun post(body: String, apiKey: String?): Pair<Int, String> {
        val connection = URL("http://127.0.0.1:$port/api/send").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        if (apiKey != null) {
            connection.setRequestProperty("X-API-Key", apiKey)
        }

        val bytes = body.toByteArray(Charsets.UTF_8)
        connection.setFixedLengthStreamingMode(bytes.size)
        connection.outputStream.use { it.write(bytes) }

        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val responseBody = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        return status to responseBody
    }

    private fun validBody(): String =
        """{"phone_number":"+919876543210","message":"Balaji Provision Store OTP: 123456. Do not share this code.","request_id":"req-1"}"""

    private class FakeSmsTransport : SmsTransport {
        val calls = mutableListOf<Pair<String, String>>()

        override fun send(phoneNumber: String, message: String): SmsSendResult {
            calls += phoneNumber to message
            return SmsSendResult.Sent
        }
    }
}
