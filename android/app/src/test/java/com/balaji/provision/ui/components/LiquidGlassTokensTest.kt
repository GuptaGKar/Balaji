package com.balaji.provision.ui.components

import androidx.compose.ui.unit.dp
import com.balaji.provision.ui.theme.BalajiDesign
import com.balaji.provision.ui.theme.BrandOrange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The numbers behind the liquid-glass treatment.
 *
 * The treatment is drawn, not composited - a rim gradient, a specular band, a bottom shade and
 * a layered fill - so nothing in the framework checks it. These tests pin the parts that would
 * rot silently and change what the customer sees: the gradient running the right way, the fill
 * staying inside the legibility band the brief allows, and the highlight never growing tall
 * enough to land on text.
 *
 * They are about numbers, not pixels: whether the glass actually looks right on a phone is a
 * device check, recorded separately in QA_LOG.md.
 */
class LiquidGlassTokensTest {

    @Test
    fun `rim stops run from the specular corner to the warm corner in order`() {
        val positions = BalajiDesign.LiquidGlass.RimStops.map { it.first }
        assertEquals(
            "the gradient must start at the top-left corner",
            0f,
            positions.first(),
            0f
        )
        assertEquals(
            "the gradient must end at the bottom-right corner",
            1f,
            positions.last(),
            0f
        )
        assertEquals(
            "rim stops must be sorted, or the gradient renders inside out",
            positions.sorted(),
            positions
        )
        assertTrue("a rim needs at least three stops to be a gradient", positions.size >= 3)
    }

    @Test
    fun `the rim fades from bright to a faint warm refraction`() {
        val stops = BalajiDesign.LiquidGlass.RimStops
        val firstAlpha = stops.first().second.alpha
        val lastAlpha = stops.last().second.alpha

        assertTrue(
            "the specular end must be brighter than the warm end",
            firstAlpha > lastAlpha
        )
        assertEquals(
            "the warm end is the brand orange, faint enough not to read as an outline",
            BrandOrange.copy(alpha = 0.35f),
            stops.last().second
        )
    }

    @Test
    fun `the default glass fill stays inside the brief's legibility band`() {
        assertTrue(
            "GlassAlpha is chosen for legibility; it must stay in the brief's band",
            BalajiDesign.GlassAlpha in 0.90f..0.96f
        )
        val (top, bottom) = BalajiDesign.LiquidGlass.fillAlphas(BalajiDesign.GlassAlpha)
        assertEquals(BalajiDesign.GlassAlpha, top, 0f)
        assertTrue(
            "the bottom of a text-bearing surface must stay at or above 0.90",
            bottom >= 0.90f
        )
        assertTrue(
            "and at or below 0.96, or the layering stops being visible",
            bottom <= 0.96f
        )
    }

    @Test
    fun `a layered fill relaxes downwards and never exceeds the chosen alpha`() {
        val (top, bottom) = BalajiDesign.LiquidGlass.fillAlphas(BalajiDesign.GlassAlpha)
        assertTrue(
            "the bottom must be more translucent than the top, or there is no layering",
            bottom < top
        )
        assertTrue("a fill can never get more opaque than the colour given", top <= 1f)
        assertTrue("nor more translucent than the colour given", bottom > 0f)
    }

    @Test
    fun `the specular band stops at the tightest content padding it decorates`() {
        // The tightest content padding on any converted surface is the unit-toggle track in
        // WeightedQuantityDialog: Modifier.padding(4.dp). A band taller than that would fade
        // out *over* the first line of content instead of before it.
        assertEquals(4.dp, BalajiDesign.LiquidGlass.SpecularHeight)
    }

    @Test
    fun `the bottom shade is a vignette, not a smudge`() {
        // The shade is taller than the tightest padding, so it does reach content. What keeps
        // that acceptable is the alpha: at 0.10 it reads as depth and cannot shift a legibility
        // decision, so this test fails if someone turns it into a real shadow.
        assertTrue(
            "shade alpha must stay faint (<= 0.10)",
            BalajiDesign.LiquidGlass.ShadeAlpha <= 0.10f
        )
        assertTrue("a shade must be visible at all", BalajiDesign.LiquidGlass.ShadeAlpha > 0f)
        assertTrue(
            "the specular highlight must be the brightest layer",
            BalajiDesign.LiquidGlass.SpecularAlpha > BalajiDesign.LiquidGlass.ShadeAlpha
        )
    }
}
