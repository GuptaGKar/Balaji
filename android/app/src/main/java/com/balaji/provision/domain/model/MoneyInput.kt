package com.balaji.provision.domain.model

/**
 * Staff enter prices in Rupees, but every stored amount is integer paise. Parsing with
 * [Double] (`text.toDouble() * 100`) is both a money bug and a validation hole: it accepts
 * `NaN`, `Infinity` and exponent notation, and it is not exact for values such as 0.29.
 * These helpers keep the conversion in integer arithmetic and reject anything that is not a
 * plain, two-decimal Rupee amount.
 */

private val RUPEES_PATTERN = Regex("""^(?:\d+)?(?:\.\d{0,2})?$""")

/**
 * Keeps a price field to digits plus at most one decimal point and two decimal places.
 * Applied on every keystroke, so pasted text such as `₹1,234.567` becomes `1234.56`.
 */
fun filterRupeesInput(raw: String): String {
    val cleaned = buildString {
        var seenDot = false
        var decimals = 0
        raw.forEach { character ->
            when {
                character.isDigit() -> {
                    if (!seenDot) {
                        append(character)
                    } else if (decimals < 2) {
                        append(character)
                        decimals += 1
                    }
                }
                character == '.' && !seenDot -> {
                    seenDot = true
                    append('.')
                }
            }
        }
    }
    if (cleaned.isEmpty() || cleaned == ".") return cleaned
    return if (cleaned.startsWith('.')) "0$cleaned" else cleaned
}

/**
 * Returns the exact paise value, or null when the text is empty, negative, uses exponent
 * notation, has more than two decimals, or would overflow a Long.
 */
fun parseRupeesToPaise(text: String): Long? {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed == "." || !RUPEES_PATTERN.matches(trimmed)) return null

    val whole = trimmed.substringBefore('.')
    val fraction = trimmed.substringAfter('.', "").padEnd(2, '0')
    val rupees = if (whole.isEmpty()) 0L else whole.toLongOrNull() ?: return null
    val paise = if (fraction.isEmpty()) 0L else fraction.toLongOrNull() ?: return null
    if (rupees > (Long.MAX_VALUE - paise) / 100L) return null
    return rupees * 100L + paise
}

/** Renders paise back into the same plain Rupee form the staff member typed. */
fun formatRupeesInput(paise: Long): String {
    val safe = paise.coerceAtLeast(0L)
    val rupees = safe / 100L
    val fraction = safe % 100L
    return if (fraction == 0L) {
        rupees.toString()
    } else {
        "$rupees.${fraction.toString().padStart(2, '0')}"
    }
}
