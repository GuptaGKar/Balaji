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

    /**
     * The liquid-glass treatment: a gradient rim, a top edge highlight and a bottom inner
     * shade, drawn over a near-opaque tinted fill.
     *
     * This is deliberately *not* a backdrop blur. `Modifier.blur` cannot blur what is behind a
     * surface at all (it blurs the element's own drawing), a real frosted panel needs either a
     * captured backdrop layer or a third-party library, and the brief's §3.2/§5 tell you to keep
     * blur shaders out of scrolling grids. So the "liquid" here is drawn with gradients: a rim
     * that catches light at the top-left and warms towards the brand orange at the bottom-right,
     * a 4 dp specular band on the top edge, and a faint shade at the bottom edge so the surface
     * reads as a thickness rather than a flat rectangle. The band stops at the tightest content
     * padding on any surface it decorates, so no highlight is ever painted over text; the shade
     * may graze content on that same tightest surface, at an alpha low enough to read as depth
     * rather than as a smudge (see [ShadeHeight]).
     */
    object LiquidGlass {

        /**
         * Rim gradient stops, top-left to bottom-right.
         *
         * The first stop is the specular side (bright, and effectively invisible on a light
         * surface, which is what a specular highlight does); the middle stop is the neutral
         * hairline that keeps a light card defined against a light page; the last stop is the
         * warm refraction, kept faint so it never reads as an orange outline.
         */
        val RimStops = listOf(
            0.00f to Color.White.copy(alpha = 0.90f),
            0.45f to Color(0xFFB9B3AD).copy(alpha = 0.70f),
            1.00f to BrandOrange.copy(alpha = 0.35f)
        )

        /** Alpha of the specular band along the top edge, fading to transparent over [SpecularHeight]. */
        const val SpecularAlpha = 0.55f

        /**
         * Height of that band. 4 dp is no more than the tightest content padding on any
         * surface this decorates (the 4 dp unit-toggle track), so the band fades out at or
         * before the first line of content and never sits on top of text.
         */
        val SpecularHeight = 4.dp

        /** Alpha of the inner shade at the bottom edge, over [ShadeHeight]. */
        const val ShadeAlpha = 0.10f

        /**
         * Height of the shade. Taller than the tightest padding (4 dp), so on the tightest
         * surface it does reach content - but at [ShadeAlpha] that is a vignette, not a
         * smudge, and it never crosses a legibility decision. Kept faint on purpose: the
         * shade is what makes the surface read as having thickness, and thickness does not
         * need to be dark.
         */
        val ShadeHeight = 10.dp

        /** Rim stroke weight, drawn centred on the shape so half of it sits inside the edge. */
        val RimWidth = 1.5.dp

        /**
         * The fill's top and bottom alpha for a given base alpha.
         *
         * The top keeps the base value - that is the value chosen for legibility - and the
         * bottom relaxes by 3.5 % so the surface reads as layered rather than flat. Starting
         * from [GlassAlpha] (0.94) this gives 0.94 → 0.902, still inside the brief's
         * 0.90-0.96 band for a surface that carries text.
         */
        fun fillAlphas(base: Float): Pair<Float, Float> = base to (base * 0.965f)
    }

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
