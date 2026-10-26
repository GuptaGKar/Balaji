package com.balaji.smsgateway

data class GatewayResponse(
    val status: Int,
    val body: String,
    val contentType: String = "application/json",
)

sealed interface SmsSendResult {
    data object Sent : SmsSendResult

    data class Failed(val reason: String) : SmsSendResult
}

interface SmsTransport {
    fun send(phoneNumber: String, message: String): SmsSendResult
}

class GatewayRequestHandler(
    private val apiKeyProvider: () -> String?,
    private val rateLimiter: SlidingWindowRateLimiter,
    private val smsTransport: SmsTransport,
) {
    fun handle(
        method: String,
        path: String,
        providedApiKey: String?,
        body: String?,
    ): GatewayResponse {
        if (method != "POST") {
            return error(405, "method_not_allowed")
        }
        if (path != SEND_PATH) {
            return error(404, "not_found")
        }
        if (!GatewayPolicy.apiKeyMatches(apiKeyProvider(), providedApiKey)) {
            return error(401, "unauthorized")
        }

        val request = GatewayPolicy.parseSendRequest(body) ?: return error(400, "invalid_request")
        if (!rateLimiter.tryAcquire()) {
            return error(429, "rate_limited")
        }

        val result = try {
            smsTransport.send(request.phoneNumber, request.message)
        } catch (_: Exception) {
            SmsSendResult.Failed("send_exception")
        }

        return when (result) {
            SmsSendResult.Sent -> GatewayResponse(200, """{"success":true}""")
            is SmsSendResult.Failed -> error(502, "send_failed")
        }
    }

    private fun error(status: Int, code: String): GatewayResponse =
        GatewayResponse(status, """{"success":false,"error":"$code"}""")

    private companion object {
        const val SEND_PATH = "/api/send"
    }
}
