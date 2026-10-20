package com.balaji.provision.data.network

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class QuoteCartRequestTest {

    @Test
    fun `quote request uses the PostgREST function parameter name`() {
        val request = QuoteCartRequest(
            p_items = listOf(CartItemRequest(product_id = "product-1", qty_base = 2))
        )

        assertEquals(
            """{"p_items":[{"product_id":"product-1","qty_base":2}]}""",
            Json.encodeToString(request)
        )
    }
}
