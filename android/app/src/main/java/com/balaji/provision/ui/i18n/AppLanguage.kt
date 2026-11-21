package com.balaji.provision.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Languages the customer app can display its own interface in.
 *
 * Only the app's chrome is translated. Store-entered content - product names,
 * descriptions, category names, addresses and manager-composed section titles -
 * is shown exactly as the store wrote it, because translating a catalogue behind
 * the manager's back would make the shelf disagree with the shelf label.
 */
enum class AppLanguage(val tag: String, val nativeLabel: String) {
    ENGLISH("en", "English"),
    HINDI("hi", "हिंदी");

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag == tag } ?: ENGLISH
    }
}

/**
 * Resolves English UI copy to the selected language.
 *
 * The English text is the key, which keeps call sites readable and means a
 * missing translation degrades to English instead of a blank label. [HindiStrings]
 * is checked by [com.balaji.provision.ui.i18n.AppStringsTest] so an entry cannot
 * silently lose its translation.
 */
@Immutable
class AppStrings(
    val language: AppLanguage,
    private val hindi: Map<String, String> = HindiStrings,
    private val dynamicHindi: List<Pair<Regex, String>> = HindiDynamicStrings
) {
    fun text(english: String): String {
        if (language != AppLanguage.HINDI) return english
        hindi[english]?.let { return it }
        // Composed labels ("Your Cart (3)") keep the store's own words for names and
        // amounts; only the sentence around them is translated.
        return dynamicHindi.firstOrNull { (pattern, _) -> pattern.matches(english) }
            ?.let { (pattern, replacement) -> pattern.replace(english, replacement) }
            ?: english
    }

    /** Translates a template such as "Add %s more for free delivery". */
    fun format(template: String, vararg args: Any): String {
        val translated = text(template)
        return if (args.isEmpty()) translated else String.format(translated, *args)
    }
}

val LocalAppStrings = staticCompositionLocalOf { AppStrings(AppLanguage.ENGLISH) }

/** Translates one string in a composable, for labels that are not rendered by [LocalizedText]. */
@Composable
fun tr(text: String): String = LocalAppStrings.current.text(text)
