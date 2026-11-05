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
}
