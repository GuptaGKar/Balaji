package com.balaji.smsgateway

import fi.iki.elonen.NanoHTTPD
import java.nio.charset.StandardCharsets

class GatewayServer(
    port: Int,
    private val handler: GatewayRequestHandler,
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        val body = if (session.method == Method.POST) readBody(session) else null
        val providedApiKey = session.headers.entries
            .firstOrNull { it.key.equals(API_KEY_HEADER, ignoreCase = true) }
            ?.value

        val response = handler.handle(
            method = session.method.name,
            path = session.uri,
            providedApiKey = providedApiKey,
            body = body,
        )

        return newFixedLengthResponse(
            SimpleStatus(response.status),
            response.contentType,
            response.body,
        ).apply {
            addHeader("Cache-Control", "no-store")
            addHeader("X-Content-Type-Options", "nosniff")
            addHeader("Referrer-Policy", "no-referrer")
        }
    }

    private fun readBody(session: IHTTPSession): String? {
        val length = session.headers.entries
            .firstOrNull { it.key.equals("content-length", ignoreCase = true) }
            ?.value
            ?.toIntOrNull()
            ?: return null

        if (length <= 0 || length > MAX_BODY_BYTES) return null

        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = session.inputStream.read(bytes, offset, length - offset)
            if (read <= 0) return null
            offset += read
        }
        return String(bytes, StandardCharsets.UTF_8)
    }

    private class SimpleStatus(private val code: Int) : Response.IStatus {
        override fun getRequestStatus(): Int = code

        override fun getDescription(): String = when (code) {
            200 -> "200 OK"
            400 -> "400 Bad Request"
            401 -> "401 Unauthorized"
            404 -> "404 Not Found"
            405 -> "405 Method Not Allowed"
            429 -> "429 Too Many Requests"
            502 -> "502 Bad Gateway"
            else -> "$code Error"
        }
    }

    private companion object {
        const val API_KEY_HEADER = "x-api-key"
        const val MAX_BODY_BYTES = 4096
    }
}
