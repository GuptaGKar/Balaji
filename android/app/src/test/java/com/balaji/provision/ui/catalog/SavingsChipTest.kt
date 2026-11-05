package com.balaji.provision.ui.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The savings indicator on Product Details.
 *
 * It is derived from the two server prices instead of from a promo field, so the one place the
 * app states a saving cannot state one that the bill will not show. These are the cases where a
 * careless implementation would invent money: a missing MRP, a price that is not actually lower,
 * and a percentage the store never set.
 */
class SavingsChipTest {

    @Test
    fun `a real saving is stated in money and as a percentage`() {
        // 2400 paise down to 2000 is ₹4.00, and 400/2400 = 16.67%. The model truncates its own
        // discountPercent to 16, so the chip has to say 16 too.
        assertEquals(
            "Save ₹4.00 (16%)",
            savingsChipLabel(mrpPaise = 2400L, pricePaise = 2000L)
        )
        assertEquals(
            "Save ₹50.00 (25%)",
            savingsChipLabel(mrpPaise = 20000L, pricePaise = 15000L)
        )
    }

    @Test
    fun `no MRP means no claim`() {
        assertNull(savingsChipLabel(mrpPaise = null, pricePaise = 2000L))
    }

    @Test
    fun `a price at or above the list price produces no chip`() {
        assertNull(savingsChipLabel(mrpPaise = 2000L, pricePaise = 2000L))
        assertNull(savingsChipLabel(mrpPaise = 1900L, pricePaise = 2000L))
        // A zero or negative list price cannot be a denominator for a percentage.
        assertNull(savingsChipLabel(mrpPaise = 0L, pricePaise = 0L))
    }

    @Test
    fun `the percentage is never rounded up into a saving that is not there`() {
        // 100 paise off 99900 is 0.1%. Truncating gives 0, and "Save ₹1.00 (0%)" is odd but true;
        // showing 1% would not be.
        assertEquals(
            "Save ₹1.00 (0%)",
            savingsChipLabel(mrpPaise = 99900L, pricePaise = 99800L)
        )
    }
}
