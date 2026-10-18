package com.balaji.provision.domain.model

import org.junit.Assert.*
import org.junit.Test

class PriceFormattingTest {

    @Test
    fun `formatPaise formats correctly`() {
        assertEquals("₹0.00", formatPaise(0))
        assertEquals("₹1.00", formatPaise(100))
        assertEquals("₹1.50", formatPaise(150))
        assertEquals("₹10.00", formatPaise(1000))
        assertEquals("₹123.45", formatPaise(12345))
        assertEquals("₹1,234.56", formatPaise(123456))
    }

    @Test
    fun `formatPrice packed`() {
        assertEquals("₹45.00 / pack", formatPrice(4500, 1, QuantityType.PACKED))
        assertEquals("₹123.45 / pack", formatPrice(12345, 1, QuantityType.PACKED))
    }

    @Test
    fun `formatPrice weighted per kg`() {
        assertEquals("₹60.00 / kg", formatPrice(6000, 1000, QuantityType.WEIGHTED))
    }

    @Test
    fun `formatPrice weighted per 100g`() {
        assertEquals("₹99.00 / 100g", formatPrice(9900, 100, QuantityType.WEIGHTED))
    }

    @Test
    fun `formatPrice weighted per g`() {
        assertEquals("₹5.00 / g", formatPrice(500, 1, QuantityType.WEIGHTED))
    }
}