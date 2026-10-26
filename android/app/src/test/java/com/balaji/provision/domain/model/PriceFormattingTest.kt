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
        assertEquals("₹12,345.67", formatPaise(1234567))
        assertEquals("₹1,23,456.78", formatPaise(12345678))
        assertEquals("₹1,23,45,678.90", formatPaise(1234567890))
        assertEquals("-₹1,23,456.78", formatPaise(-12345678))
    }

    @Test
    fun `formatWeightGrams uses integer arithmetic`() {
        assertEquals("250 g", formatWeightGrams(250))
        assertEquals("1 kg", formatWeightGrams(1000))
        assertEquals("1.5 kg", formatWeightGrams(1500))
        assertEquals("1.05 kg", formatWeightGrams(1050))
    }

    @Test
    fun `lineTotalPaise rounds half up`() {
        assertEquals(1500, lineTotalPaise(6000, 1000, 250))
        assertEquals(500, lineTotalPaise(999, 2, 1))
        assertEquals(0, lineTotalPaise(6000, 0, 250))
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