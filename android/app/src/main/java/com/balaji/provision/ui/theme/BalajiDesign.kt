package com.balaji.provision.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The orange-glass design tokens the customer app is built from.
 *
 * This exists so screens stop repeating magic numbers. The values come from the approved
 * orange-glass reference board, and each one is a starting point that has to be judged against
 * real screenshots rather than treated as settled truth.
 *
 * Two rules the reference image cannot express and that are therefore written down here:
 * transparency never wins over legibility (see GlassAlpha), and no token here may imply a
 * delivery promise - timings are server data, never a style constant.
 */
object BalajiDesign {

    /** Brand oranges. Primary is the only colour used for a purchasing action. */
    val Primary = BrandOrange
    val PrimaryDeep = BrandOrangeDeep
    val Highlight = BrandAmber
    val PeachBackground = Color(0xFFFFF4EC)
    val PeachSurface = BrandOrangeContainer
    val White = Color(0xFFFFFFFF)

    /**
     * Opacity of a glass surface that carries text.
     *
     * The reference board's earlier pill used 0.30f and product names behind it read through
     * the labels - recorded as BUG-012 and measured on a Galaxy A34 in light theme. The brief
     * now asks for 0.90-0.96 where text sits on the surface. This is the upper end because the
     * surfaces it covers are photographic product cards, not flat background.
     */
    const val GlassAlpha = 0.94f

    /** Outline weight of a glass card. Thin enough to read as a hairline at 450 dpi. */
    val CardBorder = 1.dp

    /** Elevation of a glass card. Kept low: tall shadows on every list row read as mush. */
    val CardElevation = 3.dp

    val RadiusCard = 16.dp
    val RadiusHero = 20.dp
    val RadiusPill = 999.dp

    /** Page gutter. Every screen in the customer app uses this one value. */
    val ScreenPadding = 16.dp

    /** Rhythm between sections. */
    val SectionGap = 22.dp

    /** Rhythm inside a card or row. */
    val InnerGap = 8.dp

    /**
     * Motion timings, in milliseconds, for the ADD to stepper language.
     *
     * These are deliberately short. A grocery list is scrolled fast and animated twice per
     * second by a thumb, so anything slower feels like lag. Nothing here loops or glows:
     * animation is a response to an action, not decoration.
     */
    object Motion {
        /** Press acknowledgement. Must land before the eye finishes moving. */
        const val Press = 100

        /** ADD morphing into the quantity stepper. */
        const val Expand = 250

        /** A quantity digit sliding in or out when the value changes. */
        const val NumberSlide = 170

        /** The stepper collapsing back to ADD at zero. */
        const val Collapse = 220

        /** The selected capsule travelling between navigation destinations. */
        const val NavSlide = 260

        /** A slide-to-confirm thumb returning to the start after an early release. */
        const val SpringBack = 260
    }

    /** Slide distance that counts as an intentional commit, of the available travel. */
    const val SlideCommitFraction = 0.85f

    /** Touch-target floor. Never traded away to match a mockup's tighter geometry. */
    val MinTouchTarget = 48.dp

    /** Height of the ADD pill and its expanded stepper. */
    val ControlHeight = 40.dp

    /** Height of the primary CTA (Add to Cart, checkout action). */
    val PrimaryCtaHeight = 54.dp
}
