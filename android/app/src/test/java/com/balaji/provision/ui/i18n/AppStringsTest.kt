package com.balaji.provision.ui.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStringsTest {

    private val hindi = AppStrings(AppLanguage.HINDI)
    private val english = AppStrings(AppLanguage.ENGLISH)

    @Test
    fun `English is the pass-through default`() {
        assertEquals("Your Cart", english.text("Your Cart"))
        assertEquals("Amul Milk 500 ml", english.text("Amul Milk 500 ml"))
    }

    @Test
    fun `core shopping copy is translated`() {
        listOf(
            "Home",
            "Categories",
            "Orders",
            "Basket",
            "Search products",
            "Your cart is empty",
            "Checkout",
            "Place order",
            "Out of stock",
            "Add delivery address",
            "Language",
            "Dark mode",
            "Sign in",
            "My Orders"
        ).forEach { key ->
            assertNotEquals("missing Hindi for: $key", key, hindi.text(key))
            assertTrue("blank Hindi for: $key", hindi.text(key).isNotBlank())
        }
    }

    @Test
    fun `store data is never translated`() {
        listOf(
            "Amul Milk 500 ml",
            "Aashirvaad Atta 5 kg",
            "12, MG Road, Pune",
            "Ramesh Kumar",
            "Fresh Picks"
        ).forEach { value ->
            assertEquals(value, hindi.text(value))
        }
    }

    @Test
    fun `composed labels keep the store name and amount`() {
        assertEquals("आपका कार्ट (3)", hindi.text("Your Cart (3)"))
        assertEquals("स्टॉक में (7)", hindi.text("In stock (7)"))
        assertEquals("मुफ़्त डिलीवरी के लिए ₹40.00 और जोड़ें", hindi.text("Add ₹40.00 more for free delivery"))
        assertEquals("25% छूट", hindi.text("25% OFF"))
        assertEquals("ऑर्डर #1042", hindi.text("Order #1042"))
        assertEquals("दूध, आटा, स्नैक्स खोजें…", hindi.text("Search for milk, atta, snacks…"))
        // The address header and the map's pin label embed a customer/store value that
        // must survive the translation untouched.
        assertEquals("यहाँ डिलीवरी: Ramesh Kumar", hindi.text("Deliver to Ramesh Kumar"))
        assertEquals("18.52043, 73.85674 पर पिन", hindi.text("Pin at 18.52043, 73.85674"))
        assertEquals("1 पैक", hindi.text("1 pack"))
        assertEquals("3 पैक साइज़", hindi.text("3 pack sizes"))
        // The home capability tiles are two-line labels; the newline is part of the key.
        assertEquals("विविध\nविकल्प", hindi.text("Wide\nRange"))
        assertEquals("स्थानीय\nडिलीवरी", hindi.text("Local\nDelivery"))
        assertEquals("सुरक्षित\nऑर्डर", hindi.text("Secure\nOrders"))
        assertEquals("आसान\nदोबारा ऑर्डर", hindi.text("Easy\nReorder"))
        // The product hero's accessible name embeds the store's product name verbatim.
        assertEquals(
            "Amulya Dairy Whitener की तस्वीर",
            hindi.text("Image of Amulya Dairy Whitener")
        )
    }

    @Test
    fun `second-wave copy is translated`() {
        listOf(
            "Ready to go",
            "Your order history",
            "Continue to payment",
            "Edit delivery address",
            "Everyday essentials",
            "Cash on Delivery / online",
            "Change delivery address",
            "Saving…",
            " Add new"
        ).forEach { key ->
            assertNotEquals("missing Hindi for: $key", key, hindi.text(key))
        }
    }

    @Test
    fun `every Hindi entry is non-blank and differs from its key`() {
        HindiStrings.forEach { (key, value) ->
            assertTrue("blank translation for: $key", value.isNotBlank())
            // Brand names and units are allowed to stay as they are, but a UI phrase
            // that silently repeats its English key is almost always a missed entry.
            if (key.length > 2 && key.any { it.isLetter() }) {
                assertTrue("untranslated entry: $key", value != key || key in allowedSame)
            }
        }
    }

    private val allowedSame = setOf(
        "OTP",
        "PIN",
        "COD",
        "GPS",
        "English",
        "Balaji",
        "Balaji Provision Store",
        "Provision Store"
    )
}
