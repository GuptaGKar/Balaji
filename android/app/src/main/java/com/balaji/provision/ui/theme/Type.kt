package com.balaji.provision.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The three families the orange-glass design is specified in, and the one place they are resolved.
 *
 * The brief asks for Poppins (headings), Inter (body and UI) and Manrope (prices, quantities and
 * CTA labels). The binaries are not in this repository, are not installed on this machine and may
 * not be downloaded here, so all three currently resolve to the platform sans-serif. That is a
 * deliberate stand-in, not a claim that the typography matches the reference board.
 *
 * To finish this properly, drop the licensed TTFs into
 * `android/app/src/main/res/font/` (lowercase names, plus a licence notice), then replace the
 * three `FontFamily.SansSerif` lines below with `FontFamily(R.font.xxx)`. Nothing else in the app
 * needs to change, because every screen reads a semantic role from [BalajiText] or
 * [MaterialTheme.typography] rather than naming a font.
 *
 * The fallback chain matters more than it looks: ₹, Devanagari and long Tamil place names have to
 * render, so whatever replaces these must list a fallback family rather than a single TTF.
 */
object BalajiFont {
    /** Display headings: branding, page titles, section titles, product names. */
    val Display: FontFamily = FontFamily.SansSerif

    /** Body copy, addresses, search text, navigation labels, metadata. */
    val Body: FontFamily = FontFamily.SansSerif

    /** Numerals: prices, totals, discounts, quantities, CTA labels. */
    val Numeric: FontFamily = FontFamily.SansSerif
}

/**
 * Semantic text roles. A screen picks the role that describes what the text *is*, not the size it
 * happens to want, so that the scale stays consistent across the app and one edit here re-types
 * every screen.
 *
 * Sizes are the brief's starting values. They are in sp, so they follow the system font scale;
 * anything that must not shrink (the pill's tab labels) is checked against a 2.0x layout rather
 * than being pinned to dp.
 */
object BalajiText {

    /** App branding in the header. */
    val Brand = TextStyle(
        fontFamily = BalajiFont.Display,
        fontWeight = FontWeight.Bold,
        fontSize = 23.sp,
        lineHeight = 28.sp
    )

    /** "Categories", "Your Cart", "My Orders". */
    val PageTitle = TextStyle(
        fontFamily = BalajiFont.Display,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 27.sp
    )

    /** "Shop by Category", "Buy Again", a composed section name. */
    val SectionTitle = TextStyle(
        fontFamily = BalajiFont.Display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 23.sp
    )

    /** A product name in a card or on the detail screen. Two lines at most. */
    val ProductName = TextStyle(
        fontFamily = BalajiFont.Display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 19.sp
    )

    /** The price a customer pays for one line. */
    val Price = TextStyle(
        fontFamily = BalajiFont.Numeric,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 21.sp
    )

    /** A crossed-out MRP beside [Price]. Lighter, and never the only price shown. */
    val Mrp = TextStyle(
        fontFamily = BalajiFont.Numeric,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp
    )

    /** Total payable - the biggest number in the app. */
    val Total = TextStyle(
        fontFamily = BalajiFont.Numeric,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 21.sp,
        lineHeight = 26.sp
    )

    /** The quantity digit in the stepper. */
    val Quantity = TextStyle(
        fontFamily = BalajiFont.Numeric,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 20.sp
    )

    /** Body copy and delivery addresses. */
    val Body = TextStyle(
        fontFamily = BalajiFont.Body,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )

    /** A label on a control that the customer taps to buy something. */
    val Cta = TextStyle(
        fontFamily = BalajiFont.Numeric,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    )

    /** Pack size, unit, "120 products", timestamps. */
    val Metadata = TextStyle(
        fontFamily = BalajiFont.Body,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )

    /** Bottom-navigation labels. */
    val Nav = TextStyle(
        fontFamily = BalajiFont.Body,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 15.sp
    )
}

/**
 * Material roles mapped onto the semantic scale, so existing screens that already use
 * `MaterialTheme.typography.*` inherit the new hierarchy without being rewritten.
 */
val BalajiTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = BalajiFont.Display,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp
    ),
    displayMedium = TextStyle(
        fontFamily = BalajiFont.Display,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp
    ),
    displaySmall = TextStyle(
        fontFamily = BalajiFont.Display,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = BalajiFont.Display,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    headlineMedium = BalajiText.PageTitle,
    headlineSmall = TextStyle(
        fontFamily = BalajiFont.Display,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleLarge = BalajiText.SectionTitle,
    titleMedium = BalajiText.ProductName,
    titleSmall = TextStyle(
        fontFamily = BalajiFont.Body,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = BalajiFont.Body,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = BalajiText.Body,
    bodySmall = BalajiText.Metadata,
    labelLarge = BalajiText.Cta,
    labelMedium = TextStyle(
        fontFamily = BalajiFont.Numeric,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = BalajiFont.Body,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp
    )
)
