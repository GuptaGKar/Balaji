package com.balaji.provision.ui.components

/**
 * Store-settings text such as "Same-day delivery; confirmed upon acceptance" is
 * already a sentence, while "2 hours" needs a lead-in. Prefixing blindly produced
 * "Delivery in Same-day delivery; ...", so only add the prefix when the text does
 * not already talk about delivery.
 */
fun deliveryWindowLabel(window: String): String {
    val text = window.trim()
    return if (text.contains("deliver", ignoreCase = true)) text else "Delivery in $text"
}
