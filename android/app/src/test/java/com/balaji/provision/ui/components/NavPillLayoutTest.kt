package com.balaji.provision.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The geometry the floating pill depends on.
 *
 * The pill is an overlay: it is drawn over the page instead of reserving a strip, so each screen
 * has to add the reserve to its own scroll padding. Nothing in Compose checks that a screen did
 * that, and nothing checks that the number still matches the pill's real height once someone
 * changes it. These tests pin the relationship between the two, which is the part that can rot
 * silently.
 *
 * They are about numbers, not pixels: whether the pill actually looks right over a busy page is
 * a device check, recorded separately.
 */
class NavPillLayoutTest {

    @Test
    fun `the reserve is the pill height plus its bottom margin plus room for its shadow`() {
        assertEquals(
            NAV_PILL_HEIGHT_DP + NAV_PILL_BOTTOM_MARGIN_DP + NAV_PILL_ELEVATION_ALLOWANCE_DP,
            NAV_PILL_RESERVE_DP
        )
    }

    @Test
    fun `the reserve clears the pill and the gap under it`() {
        assertTrue(
            "content must scroll past the whole pill plus its bottom margin",
            NAV_PILL_RESERVE_DP >= NAV_PILL_HEIGHT_DP + NAV_PILL_BOTTOM_MARGIN_DP
        )
    }

    @Test
    fun `a screen with the basket bar reserves the bar again on top of the pill`() {
        assertEquals(NAV_PILL_RESERVE_DP.dp, navPillReserve())
        assertEquals(
            (NAV_PILL_RESERVE_DP + MINI_CART_BAR_RESERVE_DP).dp,
            navPillReserve(basketBarVisible = true)
        )
        assertTrue(navPillReserve(basketBarVisible = true) > navPillReserve())
    }

    @Test
    fun `the basket bar reserve is at least the bar's own minimum height`() {
        // MiniCartBar asks for heightIn(min = 56.dp); the bar sits on top of the pill, so the
        // content reserve has to clear that height as well or the "add more for free delivery"
        // line ends up under the last product row.
        assertTrue(MINI_CART_BAR_RESERVE_DP >= 56)
    }

    @Test
    fun `a tab keeps a 48 dp touch target inside the pill`() {
        assertEquals(
            48,
            NAV_PILL_HEIGHT_DP - 2 * NAV_PILL_INNER_VERTICAL_PADDING_DP
        )
    }

    /**
     * The capsule is sized to the slot, and the label has to fit inside it.
     *
     * This is the shape that survived a device pass. The alternative - sizing the capsule to the
     * selected item's content so the icon and label could sit side by side like the reference
     * board - truncated "Categories" to "Categ…" at 78 dp per slot on a 360 dp phone. Clipping the
     * one word that tells you where you are is worse than the mockup's proportions.
     */
    @Test
    fun `the capsule fills its slot minus the inset on each side`() {
        val row = 312f                      // 360 dp screen - 32 dp margin - 16 dp inner padding
        val slot = row / 4f
        val (firstLeft, width) = capsuleGeometryPx(0, row, 4, 11.25f)   // 4 dp at 2.8125 px/dp
        assertEquals(11.25f, firstLeft, 0.01f)
        assertEquals(slot - 22.5f, width, 0.01f)
        assertTrue("capsule must not exceed its slot", width <= slot)

        val (thirdLeft, _) = capsuleGeometryPx(2, row, 4, 11.25f)
        assertEquals(slot * 2f + 11.25f, thirdLeft, 0.01f)
    }

    @Test
    fun `an unmeasured row or an unknown destination produces no capsule`() {
        // Modifier.width throws on a negative Dp, and the first frame has not measured yet.
        assertEquals(0f to 0f, capsuleGeometryPx(0, 0f, 4, 11.25f))
        assertEquals(0f to 0f, capsuleGeometryPx(-1, 312f, 4, 11.25f))
        assertEquals(0f to 0f, capsuleGeometryPx(0, 312f, 0, 11.25f))
        // A slot narrower than the inset twice over cannot hold a capsule.
        assertEquals(0f to 0f, capsuleGeometryPx(0, 20f, 4, 11.25f))
    }

    @Test
    fun `a tab label has to fit its slot on the narrowest phone supported`() {
        // 320 dp is the smallest width this app claims. Four slots over it leave 64 dp each after
        // the pill's own insets, which is what "Categories" at 12 sp has to live inside.
        val row = (320f - 32f - 16f) / 4f
        assertTrue("slot $row dp is too narrow for the longest tab label", row >= 60f)
    }
}
