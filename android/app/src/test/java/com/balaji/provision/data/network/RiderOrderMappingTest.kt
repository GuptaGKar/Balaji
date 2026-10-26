package com.balaji.provision.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderOrderMappingTest {

    @Test
    fun `maps rider order with pinned address and items`() {
        val order = response().toDomain()

        assertEquals("BPS-261022-0001", order.orderNumber)
        assertEquals("ready_for_dispatch", order.status)
        assertTrue(order.isCod)
        assertTrue(order.isActive)
        assertEquals(12_500L, order.grandTotalPaise)
        assertEquals("Asha Patil", order.address?.recipientName)
        assertEquals(
            "12 MG Road, Near Park, Kothrud, Pune, Maharashtra, 411038",
            order.address?.displayAddress
        )
        assertTrue(order.address?.hasCoordinates == true)
        assertEquals(2, order.items.size)
        assertEquals("500 g", order.items.first().displayQty)
        assertEquals(
            listOf("out_for_delivery"),
            order.actions.map { it.toStatus }
        )
    }

    @Test
    fun `out for delivery offers delivered and failed actions`() {
        val order = response().copy(status = "out_for_delivery").toDomain()

        assertEquals(
            listOf("delivered", "delivery_failed"),
            order.actions.map { it.toStatus }
        )
    }

    @Test
    fun `completed orders have no actions`() {
        val order = response().copy(status = "delivered").toDomain()

        assertTrue(order.actions.isEmpty())
        assertTrue(!order.isActive)
    }

    @Test
    fun `missing snapshot and display quantity fall back safely`() {
        val order = response().copy(
            address_snapshot = null,
            items = listOf(
                RiderOrderItemResponse(
                    name = "Toor dal",
                    qty = 2,
                    display_qty = null,
                    unit = "pack",
                    total = 9_000L
                )
            )
        ).toDomain()

        assertNull(order.address)
        assertEquals("2 pack", order.items.single().displayQty)
    }

    private fun response() = RiderOrderResponse(
        id = "order-1",
        public_order_number = "BPS-261022-0001",
        address_snapshot = RiderAddressSnapshot(
            recipient_name = "Asha Patil",
            phone = "9876543210",
            address_lines = "12 MG Road",
            landmark = "Near Park",
            area = "Kothrud",
            city = "Pune",
            state = "Maharashtra",
            postal_code = "411038",
            lat = 18.5074,
            lng = 73.8077
        ),
        payment_method = "cod",
        cod_collected_paise = null,
        grand_total_paise = 12_500L,
        status = "ready_for_dispatch",
        customer_note = "Call before arriving",
        created_at = "2026-10-22T09:15:00Z",
        items = listOf(
            RiderOrderItemResponse(
                id = "item-1",
                name = "Basmati rice",
                qty = 500,
                display_qty = "500 g",
                unit = "g",
                quantity_type = "weighted",
                unitPrice = 8_000L,
                total = 8_000L
            ),
            RiderOrderItemResponse(
                id = "item-2",
                name = "Toor dal",
                qty = 1,
                display_qty = "1 pack",
                unit = "pack",
                quantity_type = "packed",
                unitPrice = 4_500L,
                total = 4_500L
            )
        )
    )
}
