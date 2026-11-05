package com.balaji.provision.ui.components

/**
 * Whole-rupee price for the storefront surfaces: the composed Home and Categories rails.
 *
 * Money stays integer paise everywhere. This only formats for display, so nothing here can
 * change what a quote, cart or bill shows - those keep [formatPaise].
 *
 * Rounding is half up, matching the per-line money rule, and the arithmetic is integer only so
 * no floating-point drift can appear in a price. An absent price renders as an em dash rather
 * than ₹0, because "we have no price for this" and "this is free" are different facts.
 */
fun formatRupeesWhole(paise: Long?): String {
    if (paise == null) return "—"
    val wholeRupees = Math.floorDiv(paise + 50, 100)
    return "₹" + groupIndian(wholeRupees)
}

/**
 * Indian digit grouping: 1234567 becomes "12,34,567" - last three, then pairs.
 *
 * Deliberately not `String.format("%,d")` with a locale: the grouping that produces depends on
 * the device locale, so an English-locale phone would render "1,234,567" and the same price
 * would look different on two devices in the same store.
 */
private fun groupIndian(rupees: Long): String {
    val sign = if (rupees < 0) "-" else ""
    val digits = (if (rupees < 0) -rupees else rupees).toString()
    if (digits.length <= 3) return sign + digits

    val last3 = digits.takeLast(3)
    val rest = digits.dropLast(3)
    // The remaining digits group in PAIRS counting from the right, which is easiest to see by
    // walking the reversed string in twos and reversing back. Chunking the string as-is groups
    // from the left and yields the western "1,234,567".
    val groupedRest = rest.reversed().chunked(2).joinToString(",").reversed()
    return "$sign$groupedRest,$last3"
}
