package com.balaji.provision.ui.orders

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class OrderTimestampTest {

    private lateinit var previousTimeZone: TimeZone
    private lateinit var previousLocale: Locale

    @Before
    fun setUp() {
        previousTimeZone = TimeZone.getDefault()
        previousLocale = Locale.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(previousTimeZone)
        Locale.setDefault(previousLocale)
    }

    @Test
    fun `parses supabase timestamp with fractional seconds`() {
        val formatted = formatOrderTimestamp(
            "2026-10-23T13:40:12.123456+00:00",
            pattern = "yyyy-MM-dd HH:mm"
        )
        assertEquals("2026-10-23 13:40", formatted)
    }

    @Test
    fun `parses timestamp without fractional seconds`() {
        val formatted = formatOrderTimestamp(
            "2026-01-02T05:06:07+00:00",
            pattern = "yyyy-MM-dd HH:mm"
        )
        assertEquals("2026-01-02 05:06", formatted)
    }

    @Test
    fun `default pattern produces a readable stamp`() {
        val formatted = formatOrderTimestamp("2026-10-23T13:40:12+00:00")
        assertNotNull(formatted)
        assertEquals("23 Oct, 1:40 PM", formatted)
    }

    @Test
    fun `blank and null values return null`() {
        assertNull(formatOrderTimestamp(null))
        assertNull(formatOrderTimestamp(""))
        assertNull(formatOrderTimestamp("   "))
    }

    @Test
    fun `short values return null`() {
        assertNull(formatOrderTimestamp("2026-10-23"))
        assertNull(formatOrderTimestamp("2026-10-23T13:40"))
    }

    @Test
    fun `invalid values return null`() {
        assertNull(formatOrderTimestamp("not-a-timestamp-at-all"))
        assertNull(formatOrderTimestamp("2026-13-40T25:61:61+00:00"))
    }
}
