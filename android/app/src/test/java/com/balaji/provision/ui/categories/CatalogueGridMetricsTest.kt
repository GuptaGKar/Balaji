package com.balaji.provision.ui.categories

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The categories page is a flat grid with no section headings. These tests pin
 * the responsive geometry so a phone keeps four columns while a tablet widens
 * the grid instead of stretching four tiles across the screen.
 */
class CatalogueGridMetricsTest {

    @Test
    fun `very narrow phones use three columns and compact padding`() {
        val metrics = catalogueGridMetrics(280)
        assertEquals(3, metrics.columns)
        assertEquals(12, metrics.horizontalPaddingDp)
        assertEquals(10, metrics.gapDp)
        assertEquals(12, metrics.labelFontSizeSp)
    }

    @Test
    fun `standard phones use four columns`() {
        listOf(320, 360, 393, 411, 480, 599).forEach { width ->
            assertEquals("width $width", 4, catalogueGridMetrics(width).columns)
        }
    }

    @Test
    fun `tablets widen the grid instead of stretching tiles`() {
        assertEquals(6, catalogueGridMetrics(600).columns)
        assertEquals(6, catalogueGridMetrics(800).columns)
        assertEquals(8, catalogueGridMetrics(840).columns)
        assertEquals(8, catalogueGridMetrics(1000).columns)
    }

    @Test
    fun `padding and labels grow with the window`() {
        val phone = catalogueGridMetrics(360)
        val tablet = catalogueGridMetrics(700)
        val wide = catalogueGridMetrics(1000)

        assertEquals(16, phone.horizontalPaddingDp)
        assertEquals(24, tablet.horizontalPaddingDp)
        assertEquals(32, wide.horizontalPaddingDp)

        assertEquals(13, phone.labelFontSizeSp)
        assertEquals(14, tablet.labelFontSizeSp)
        assertEquals(14, wide.labelFontSizeSp)
    }

    @Test
    fun `gaps stay smaller than the outer padding`() {
        listOf(280, 320, 360, 480, 600, 800, 1000, 1280).forEach { width ->
            val metrics = catalogueGridMetrics(width)
            assertTrue(
                "width $width gap=${metrics.gapDp} padding=${metrics.horizontalPaddingDp}",
                metrics.gapDp <= metrics.horizontalPaddingDp
            )
        }
    }
}
