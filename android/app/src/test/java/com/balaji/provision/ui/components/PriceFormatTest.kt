package com.balaji.provision.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Whole-rupee display for the composed storefront rails.
 *
 * The boundary cases matter more than the round ones: money is stored in paise and the per-line
 * rule is half up, so a formatter that rounds half-even or truncates would quietly disagree with
 * the server's quote. Indian grouping is tested explicitly because the obvious implementation,
 * String.format with a locale, renders differently depending on the device's language.
 */
class PriceFormatTest {

    @Test
    fun `exact rupees render without decimals`() {
        assertEquals("₹60", formatRupeesWhole(6000))
        assertEquals("₹1", formatRupeesWhole(100))
        assertEquals("₹0", formatRupeesWhole(0))
    }

    @Test
    fun `rounding is half up at the boundary`() {
        assertEquals("₹60", formatRupeesWhole(6049))
        assertEquals("₹61", formatRupeesWhole(6050))
        assertEquals("₹61", formatRupeesWhole(6099))
        assertEquals("₹0", formatRupeesWhole(49))
        assertEquals("₹1", formatRupeesWhole(50))
    }

    @Test
    fun `grouping is Indian, not western`() {
        assertEquals("₹1,000", formatRupeesWhole(100000))
        assertEquals("₹15,000", formatRupeesWhole(1500000))
        assertEquals("₹12,34,567", formatRupeesWhole(123456700))
        assertEquals("₹1,00,00,000", formatRupeesWhole(999999999))
    }

    @Test
    fun `no output ever carries a decimal point`() {
        for (paise in listOf(0L, 1L, 99L, 100L, 6050L, 123456700L, 999999999L)) {
            val rendered = formatRupeesWhole(paise)
            assertFalse("decimal point in $rendered for $paise", rendered.contains('.'))
        }
    }

    @Test
    fun `an absent price is not shown as free`() {
        assertEquals("—", formatRupeesWhole(null))
        assertEquals("₹0", formatRupeesWhole(0))
    }

    @Test
    fun `small amounts are not grouped`() {
        assertEquals("₹999", formatRupeesWhole(99900))
        assertEquals("₹1,000", formatRupeesWhole(100000))
    }
}
