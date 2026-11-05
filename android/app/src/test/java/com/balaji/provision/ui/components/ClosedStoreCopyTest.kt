package com.balaji.provision.ui.components

import com.balaji.provision.ui.home.homeServiceTiles
import com.balaji.provision.ui.home.storeStatusBadgeLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The closed-store wording, pinned as text.
 *
 * The Store Closed mockup says "We will deliver it tomorrow (7:00 AM - 11:00 AM)". This build has
 * no scheduling column, no slot RPC and no manager toggle, and `place_order` refuses an order
 * while `store_open` is false - so a sentence promising tomorrow morning would be a promise the
 * software cannot keep, made by the screen that a customer trusts most. These tests exist so that
 * the wording cannot drift back towards the mockup without somebody deciding to change them.
 */
class ClosedStoreCopyTest {

    @Test
    fun `scheduling is off, because nothing on the server supports it yet`() {
        // If this ever starts failing, it should only be because a real scheduled-delivery
        // migration, an availability RPC and an owner-approved setting all landed first.
        assertFalse(ScheduledOrderingEnabled)
    }

    @Test
    fun `no closed-store sentence promises a day or a time`() {
        val forbidden = listOf("tomorrow", "am", "pm", "min", "hour", "today", "7:00", "11:00")
        for (place in ClosedPanelPlace.values()) {
            val copy = closedCopyFor(place, schedulingEnabled = false)
            val text = listOf(copy.title, copy.body, copy.footnote.orEmpty())
                .joinToString(" ")
                .lowercase()
            forbidden.forEach { word ->
                assertFalse("$place must not say '$word': $text", text.contains(word))
            }
        }
    }

    @Test
    fun `every place says browsing works and checkout does not`() {
        ClosedPanelPlace.values().forEach { place ->
            val copy = closedCopyFor(place, schedulingEnabled = false)
            assertEquals("Store is Closed", copy.title)
            // The customer needs to know the basket survives; the store needs to know the order
            // is not silently queued.
            assertFalse(copy.body.isBlank())
        }
        // The cart is the screen where the refusal is felt, so it names checkout.
        val cart = closedCopyFor(ClosedPanelPlace.Cart, schedulingEnabled = false)
        assertFalse(cart.body.contains("reviewed later"))
    }

    @Test
    fun `the scheduling sentence still does not invent a window`() {
        // Even with scheduling on, the window is per-order server data. The panel may say a
        // window will be confirmed; it may not say which one.
        val copy = closedCopyFor(ClosedPanelPlace.Home, schedulingEnabled = true)
        val text = "${copy.title} ${copy.body} ${copy.footnote.orEmpty()}".lowercase()
        assertFalse(text.contains("7:00"))
        assertFalse(text.contains("11:00"))
        assertFalse(text.contains("tomorrow"))
    }

    @Test
    fun `the header badge falls back to a status when no window is configured`() {
        // An empty configuration must not produce a blank pill or a made-up duration.
        assertEquals("Store Open", storeStatusBadgeLabel(storeOpen = true, estimatedWindow = null))
        assertEquals("Store Open", storeStatusBadgeLabel(storeOpen = true, estimatedWindow = "  "))
        assertEquals("Store Closed", storeStatusBadgeLabel(storeOpen = false, estimatedWindow = "30-60 min"))
        // When a window is configured, it is quoted as the store wrote it, not reworded.
        assertEquals(
            "Delivery in 30-60 min",
            storeStatusBadgeLabel(storeOpen = true, estimatedWindow = "30-60 min")
        )
    }

    @Test
    fun `a closed store never shows a delivery window`() {
        // The window describes how long a live order takes. Quoting it while the store refuses
        // orders invites the customer to wait for a delivery that will never be placed.
        val closed = storeStatusBadgeLabel(storeOpen = false, estimatedWindow = "30-60 min")
        assertFalse(closed.contains("30-60"))
    }

    @Test
    fun `the service strip only lists capabilities that exist`() {
        val tiles = homeServiceTiles(onlinePaymentEnabled = false, hasDeliveryPin = true)
        assertEquals(4, tiles.size)
        // Online payments are disabled in this build, so the strip must not claim UPI or cards.
        assertFalse(tiles.any { it.label.contains("UPI", ignoreCase = true) })
        assertTrue(tiles.any { it.label == "Cash on Delivery" })

        val online = homeServiceTiles(onlinePaymentEnabled = true, hasDeliveryPin = false)
        assertTrue(online.any { it.label.contains("UPI") })
        assertFalse(online.any { it.label == "Cash on Delivery" })
        // No address yet: the claim becomes the instruction, not a promise about coverage.
        assertTrue(online.any { it.label == "Set your address" })
    }

    @Test
    fun `the strip makes no timing or hygiene claim`() {
        val words = listOf("min", "fast", "hour", "hygienic", "safe", "free")
        homeServiceTiles(true, true).forEach { tile ->
            val text = "${tile.label} ${tile.detail}".lowercase()
            words.forEach { word ->
                assertFalse("tile claims '$word': $text", text.contains(word))
            }
        }
    }
}
