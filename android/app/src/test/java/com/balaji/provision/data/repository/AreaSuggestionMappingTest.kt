package com.balaji.provision.data.repository

import com.balaji.provision.data.location.SuggestionType
import com.balaji.provision.data.location.chipLabel
import com.balaji.provision.data.network.AreaSuggestionRequest
import com.balaji.provision.data.network.AreaSuggestionResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M4 wire contract. These names have to be the server's exactly: a property renamed to camelCase
 * decodes to null without any error, which in the address form means a chip that appears to work
 * and fills nothing. Suite 044 covers the server; this covers the device side of the seam.
 */
class AreaSuggestionMappingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `the request body uses the server parameter name and leaks no camelCase`() {
        val encoded = json.encodeToString(AreaSuggestionRequest.serializer(), AreaSuggestionRequest(p_query = "kor"))
        // p_limit is deliberately absent: kotlinx does not encode a value that equals its
        // default, and the function declares DEFAULT 8. Enabling encodeDefaults here would
        // change the body of every other request in the app, so the server default is relied on
        // instead - and suite 044 pins what that default does.
        assertEquals(setOf("p_query"), json.parseToJsonElement(encoded).jsonObjectKeys())
        assertTrue(encoded.contains("\"p_query\":\"kor\""))
        assertFalse("a renamed property would decode to null on the server", encoded.contains("pQuery"))
    }

    @Test
    fun `an area whose label already is its PIN does not repeat it`() {
        val row = json.decodeFromString(
            AreaSuggestionResponse.serializer(),
            """{"type":"area","id":"a5","kind":"pincode","value":"560038","label":"560038","has_boundary":false}"""
        )
        val mapped = row.toDomain()!!
        assertNull(mapped.sublabel)
        assertEquals("560038", mapped.chipLabel())
    }

    @Test
    fun `an area row decodes into a completion that carries its pin and centroid`() {
        val row = json.decodeFromString(
            AreaSuggestionResponse.serializer(),
            """
            {"type":"area","id":"6c02a5e6","kind":"pincode","value":"560038","label":"Indiranagar",
             "has_boundary":true,"lat":12.97,"lng":77.59,"priority":10}
            """.trimIndent()
        )
        val mapped = row.toDomain()!!

        assertEquals(SuggestionType.AREA, mapped.type)
        assertEquals("6c02a5e6", mapped.id)
        assertEquals("Indiranagar", mapped.label)
        assertEquals("Indiranagar · PIN 560038", mapped.chipLabel())
        val centroid = mapped.point!!
        assertEquals(12.97, centroid.latitude, 0.0001)
        assertEquals(77.59, centroid.longitude, 0.0001)
    }

    @Test
    fun `an address row decodes with the fields the form needs to fill`() {
        val row = json.decodeFromString(
            AreaSuggestionResponse.serializer(),
            """
            {"type":"address","id":"a1","label":"Work","recipient_name":"Ravi","phone":"9876543210",
             "address_lines":"42 MG Road","landmark":"Opposite metro","area":"Indiranagar",
             "city":"Bengaluru","state":"Karnataka","postal_code":"560038","lat":12.98,"lng":77.61}
            """.trimIndent()
        )
        val mapped = row.toDomain()!!

        assertEquals(SuggestionType.SAVED_ADDRESS, mapped.type)
        assertEquals("Ravi", mapped.recipientName)
        assertEquals("9876543210", mapped.phone)
        assertEquals("42 MG Road", mapped.addressLines)
        assertEquals("Opposite metro", mapped.landmark)
        assertEquals("Indiranagar", mapped.area)
        assertEquals("Bengaluru", mapped.city)
        assertEquals("Karnataka", mapped.state)
        assertEquals("560038", mapped.postalCode)
        assertEquals("Indiranagar, Bengaluru", mapped.sublabel)
    }

    @Test
    fun `an area with no boundary has no location to jump to`() {
        val row = json.decodeFromString(
            AreaSuggestionResponse.serializer(),
            """{"type":"area","id":"a2","kind":"neighbourhood","value":"PGI","label":"PGI","has_boundary":false}"""
        )
        assertNull(row.toDomain()!!.point)
    }

    @Test
    fun `a row the app cannot classify is dropped rather than shown`() {
        // Better no chip than a chip that fills the wrong thing.
        assertNull(
            json.decodeFromString(AreaSuggestionResponse.serializer(), """{"id":"x","label":"Mystery"}""")
                .toDomain()
        )
        assertNull(
            json.decodeFromString(AreaSuggestionResponse.serializer(), """{"type":"area","label":"No id"}""")
                .toDomain()
        )
        assertNull(
            json.decodeFromString(AreaSuggestionResponse.serializer(), """{"type":"area","id":"y"}""")
                .toDomain()
        )
    }

    private fun kotlinx.serialization.json.JsonElement.jsonObjectKeys(): Set<String> =
        (this as kotlinx.serialization.json.JsonObject).keys
}
