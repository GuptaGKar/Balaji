package com.balaji.provision.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyInputTest {

    @Test
    fun `parses whole and fractional rupees exactly`() {
        assertEquals(0L, parseRupeesToPaise("0"))
        assertEquals(100L, parseRupeesToPaise("1"))
        assertEquals(150L, parseRupeesToPaise("1.5"))
        assertEquals(150L, parseRupeesToPaise("1.50"))
        assertEquals(105L, parseRupeesToPaise("1.05"))
        assertEquals(29L, parseRupeesToPaise("0.29"))
        assertEquals(50L, parseRupeesToPaise(".5"))
        assertEquals(1200L, parseRupeesToPaise("12."))
        assertEquals(123456L, parseRupeesToPaise("1234.56"))
    }

    @Test
    fun `rejects non-money and over-precise input`() {
        assertNull(parseRupeesToPaise(""))
        assertNull(parseRupeesToPaise("   "))
        assertNull(parseRupeesToPaise("."))
        assertNull(parseRupeesToPaise("-1"))
        assertNull(parseRupeesToPaise("1.234"))
        assertNull(parseRupeesToPaise("1.2.3"))
        assertNull(parseRupeesToPaise("NaN"))
        assertNull(parseRupeesToPaise("Infinity"))
        assertNull(parseRupeesToPaise("1e5"))
        assertNull(parseRupeesToPaise("₹10"))
        assertNull(parseRupeesToPaise("1,000"))
    }

    @Test
    fun `rejects values that would overflow paise`() {
        assertNull(parseRupeesToPaise("92233720368547758.08"))
        assertEquals(Long.MAX_VALUE, parseRupeesToPaise("92233720368547758.07"))
    }

    @Test
    fun `filters typing and pasted text to a plain rupee amount`() {
        assertEquals("1234.56", filterRupeesInput("₹1,234.567"))
        assertEquals("1.23", filterRupeesInput("1.2.3"))
        assertEquals("0.5", filterRupeesInput(".5"))
        assertEquals("", filterRupeesInput("abc"))
        // A lone dot is kept so the next digit can complete ".5" -> "0.5". Parsing it still
        // returns null, so Save stays disabled until the amount is a real one.
        assertEquals(".", filterRupeesInput("."))
    }

    @Test
    fun `formats paise without losing trailing zeroes`() {
        assertEquals("0", formatRupeesInput(0))
        assertEquals("1", formatRupeesInput(100))
        assertEquals("1.50", formatRupeesInput(150))
        assertEquals("123.05", formatRupeesInput(12305))
        assertEquals("9.99", formatRupeesInput(999))
    }
}
