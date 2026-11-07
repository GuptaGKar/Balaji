package com.balaji.provision.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.balaji.provision.R

/**
 * The three families the orange-glass design is specified in (brief section 3.3), and the one
 * place they are resolved. Every screen reads a semantic role from [BalajiText] or
 * [MaterialTheme.typography] rather than naming a font, so this object is the whole typography
 * wiring.
 *
 * The binaries are OFL-licensed files from Google Fonts, bundled in
 * `android/app/src/main/res/font/` under lowercase resource names; the licence text travels with
 * them in `android/app/FONTS-OFL.txt`. Inter and Manrope ship upstream as variable fonts, so what
 * is bundled here are static instances pinned to the weights this app actually uses.
 *
 * Glyph fallback is the platform's, not a second family listed here: a `FontFamily` in Compose
 * 1.5 is a flat list of weighted fonts, and Android's text stack falls through to the system fonts
 * for any glyph the family does not carry - which matters, because Inter and Manrope carry no
 * Devanagari and a long Hindi or Tamil place name still has to render. ₹ (U+20B9) is present in all
 * three bundled families, which is what prices need.
 */
object BalajiFont {
    /** Display headings: branding, page titles, section titles, product names. */
    val Display: FontFamily = FontFamily(
        Font(R.font.poppins_regular, FontWeight.Normal),
        Font(R.font.poppins_medium, FontWeight.Medium),
        Font(R.font.poppins_semibold, FontWeight.SemiBold),
        Font(R.font.poppins_bold, FontWeight.Bold)
    )

    /** Body copy, addresses, search text, navigation labels, metadata. */
    val Body: FontFamily = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold)
    )

    /**
     * Numerals: prices, totals, discounts, quantities, CTA labels. Manrope starts at Medium, so a
     * `FontWeight.Normal` request picks Medium rather than rendering at the wrong weight.
     */
    val Numeric: FontFamily = FontFamily(
        Font(R.font.manrope_medium, FontWeight.Medium),
        Font(R.font.manrope_semibold, FontWeight.SemiBold),
        Font(R.font.manrope_bold, FontWeight.Bold),
        Font(R.font.manrope_extrabold, FontWeight.ExtraBold)
    )
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
