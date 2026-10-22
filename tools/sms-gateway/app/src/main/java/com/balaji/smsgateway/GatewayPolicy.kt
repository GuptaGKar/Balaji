package com.balaji.smsgateway

import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class SendSmsRequest(
    val phoneNumber: String,
    val message: String,
    val requestId: String?,
)

@Serializable
private data class SendSmsPayload(
    val phone_number: String? = null,
    val message: String? = null,
    val request_id: String? = null,
)

object GatewayPolicy {
    const val MAX_MESSAGE_LENGTH = 320
    const val MIN_API_KEY_LENGTH = 32

    private val json = Json { ignoreUnknownKeys = true }
    private val indianMobile = Regex("^\\+91[6-9]\\d{9}$")
    private val sixDigitOtp = Regex("(?<!\\d)\\d{6}(?!\\d)")

    fun isValidApiKey(value: String?): Boolean =
        value != null && value.trim().length >= MIN_API_KEY_LENGTH

    fun parseSendRequest(body: String?): SendSmsRequest? {
        if (body.isNullOrBlank()) return null

        val payload = try {
            json.decodeFromString<SendSmsPayload>(body)
        } catch (_: Exception) {
            return null
        }

        val phone = payload.phone_number?.trim().orEmpty()
        val message = payload.message ?: return null
        val requestId = payload.request_id?.trim()?.takeIf { it.isNotEmpty() }

        if (!indianMobile.matches(phone)) return null
        if (message.isBlank() || message.length > MAX_MESSAGE_LENGTH) return null
        if (!sixDigitOtp.containsMatchIn(message)) return null
        if (requestId != null && (requestId.length > 128 || !requestId.all(::isSafeRequestIdChar))) return null

        return SendSmsRequest(phoneNumber = phone, message = message, requestId = requestId)
    }

    fun apiKeyMatches(expected: String?, provided: String?): Boolean {
        if (expected.isNullOrBlank() || provided.isNullOrBlank()) return false
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            provided.toByteArray(Charsets.UTF_8),
        )
    }

    private fun isSafeRequestIdChar(char: Char): Boolean =
        char.isLetterOrDigit() || char == '-' || char == '_' || char == '.' || char == ':'
}
