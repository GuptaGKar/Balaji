package com.balaji.provision.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class WeightedQuantityInputTest {

    @Test
    fun `parses grams as whole integers`() {
        assertEquals(500, parseWeightInput("500", isKilograms = false))
        assertEquals(1, parseWeightInput("1", isKilograms = false))
        assertEquals(0, parseWeightInput("", isKilograms = false))
        assertEquals(0, parseWeightInput("abc", isKilograms = false))
    }

    @Test
    fun `parses kilograms into grams`() {
        assertEquals(1500, parseWeightInput("1.5", isKilograms = true))
        assertEquals(250, parseWeightInput("0.25", isKilograms = true))
        assertEquals(1200, parseWeightInput("1.2", isKilograms = true))
        assertEquals(1234, parseWeightInput("1.234", isKilograms = true))
        assertEquals(0, parseWeightInput("1.2.3", isKilograms = true))
    }

    @Test
    fun `formats grams without decimals`() {
        assertEquals("250", formatWeightInput(250, isKilograms = false))
        assertEquals("0", formatWeightInput(0, isKilograms = false))
    }

    @Test
    fun `formats kilograms compactly`() {
        assertEquals("1", formatWeightInput(1000, isKilograms = true))
        assertEquals("1.5", formatWeightInput(1500, isKilograms = true))
        assertEquals("0.25", formatWeightInput(250, isKilograms = true))
        assertEquals("1.05", formatWeightInput(1050, isKilograms = true))
    }

    @Test
    fun `kilogram formatting round-trips through parsing`() {
        val grams = 1_750L
        val text = formatWeightInput(grams, isKilograms = true)
        assertEquals(grams, parseWeightInput(text, isKilograms = true))
    }
}
