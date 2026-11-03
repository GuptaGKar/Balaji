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

    @Test
    fun `quote carries the drop point as numbers when the address has one`() {
        val request = QuoteCartRequest(
            p_items = listOf(CartItemRequest(product_id = "product-1", qty_base = 2)),
            p_pin_code = "247232",
            p_lat = 12.9716,
            p_lng = 77.5946
        )

        // PostgREST resolves these by name against quote_cart(jsonb, text, text, uuid,
        // double precision, double precision); a string would be rejected by the cast.
        assertEquals(
            """{"p_items":[{"product_id":"product-1","qty_base":2}],""" +
                """"p_pin_code":"247232","p_lat":12.9716,"p_lng":77.5946}""",
            Json.encodeToString(request)
        )
    }

    @Test
    fun `an address without a fix quotes on the pin rule alone`() {
        val request = QuoteCartRequest(
            p_items = listOf(CartItemRequest(product_id = "product-1", qty_base = 2)),
            p_pin_code = "247232"
        )

        // Omitting the keys lets the RPC defaults apply, which is what keeps an address
        // saved before the map existed quoting exactly as it does today.
        assertEquals(
            """{"p_items":[{"product_id":"product-1","qty_base":2}],"p_pin_code":"247232"}""",
            Json.encodeToString(request)
        )
    }

    @Test
    fun `availability request names the rpc parameters`() {
        assertEquals(
            """{"p_pin_code":"247232","p_lat":12.9716,"p_lng":77.5946,"p_eligible_subtotal":10000}""",
            Json.encodeToString(
                DeliveryAvailabilityRequest(
                    p_pin_code = "247232",
                    p_lat = 12.9716,
                    p_lng = 77.5946,
                    p_eligible_subtotal = 10_000L
                )
            )
        )
        assertEquals(
            """{"p_pin_code":"247232"}""",
            Json.encodeToString(DeliveryAvailabilityRequest(p_pin_code = "247232"))
        )
    }
}
