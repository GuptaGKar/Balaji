package com.balaji.provision.data.location

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The overlay geometry is display-only, but a wrong ring here would draw a wrong
 * delivery area, so its parsing and point tests are pinned.
 */
class ZoneGeometryTest {

    private fun element(text: String) = Json.parseToJsonElement(text)

    private val square = listOf(
        GeoPoint(12.0, 77.0),
        GeoPoint(12.0, 78.0),
        GeoPoint(13.0, 78.0),
        GeoPoint(13.0, 77.0),
        GeoPoint(12.0, 77.0)
    )

    @Test
    fun `reads a geojson polygon exterior ring`() {
        val json = element(
            """{"type":"Polygon","coordinates":[[[77,12],[78,12],[78,13],[77,13],[77,12]]]}"""
        )

        val ring = ZoneGeometry.polygonRing(json)

        assertEquals(5, ring?.size)
        assertEquals(GeoPoint(12.0, 77.0), ring?.first())
        assertEquals(GeoPoint(13.0, 78.0), ring?.get(2))
    }

    @Test
    fun `ignores anything that is not a usable polygon`() {
        assertNull(ZoneGeometry.polygonRing(null))
        assertNull(ZoneGeometry.polygonRing(element("""{"type":"Point","coordinates":[77,12]}""")))
        assertNull(ZoneGeometry.polygonRing(element("""{"type":"Polygon"}""")))
        assertNull(ZoneGeometry.polygonRing(element("""{"type":"Polygon","coordinates":[]}""")))
        // Two corners cannot enclose anything.
        assertNull(
            ZoneGeometry.polygonRing(
                element("""{"type":"Polygon","coordinates":[[[77,12],[78,13],[77,12]]]}""")
            )
        )
        // A non-numeric coordinate must not become a spike on the map.
        assertNull(
            ZoneGeometry.polygonRing(
                element("""{"type":"Polygon","coordinates":[[[77,"x"],[78,12],[78,13]]]}""")
            )
        )
    }

    @Test
    fun `point inside a ring is covered and outside is not`() {
        assertTrue(ZoneGeometry.contains(square, GeoPoint(12.5, 77.5)))
        assertFalse(ZoneGeometry.contains(square, GeoPoint(14.0, 77.5)))
        assertFalse(ZoneGeometry.contains(square, GeoPoint(12.5, 79.0)))
        assertFalse(ZoneGeometry.contains(square, GeoPoint(-20.0, 40.0)))
    }

    @Test
    fun `concave ring is tested by even odd ray casting`() {
        // A U shape: the notch in the middle is genuinely outside the polygon.
        val notch = listOf(
            GeoPoint(0.0, 0.0), GeoPoint(0.0, 6.0), GeoPoint(6.0, 6.0), GeoPoint(6.0, 4.0),
            GeoPoint(2.0, 4.0), GeoPoint(2.0, 0.0), GeoPoint(0.0, 0.0)
        )

        assertTrue(ZoneGeometry.contains(notch, GeoPoint(1.0, 1.0)))
        assertTrue(ZoneGeometry.contains(notch, GeoPoint(5.0, 5.0)))
        assertFalse("the notch is open to the outside", ZoneGeometry.contains(notch, GeoPoint(3.0, 1.0)))
    }

    @Test
    fun `empty rings never cover a point`() {
        assertFalse(ZoneGeometry.contains(emptyList(), GeoPoint(12.0, 77.0)))
    }

    @Test
    fun `feature collection closes every ring and keeps its properties`() {
        val openRing = listOf(
            GeoPoint(12.0, 77.0), GeoPoint(12.0, 78.0), GeoPoint(13.0, 78.0)
        )
        val zones = listOf(DeliveryZone("z-1", "Inner belt", 10, openRing))

        val geojson = ZoneGeometry.toFeatureCollection(zones)

        assertTrue(geojson.contains("\"type\":\"FeatureCollection\""))
        assertTrue(geojson.contains("\"label\":\"Inner belt\""))
        assertTrue(geojson.contains("\"priority\":10"))
        // The ring was open in, so the writer must close it: first pair repeats last.
        assertTrue(geojson.contains("[77.0,12.0]]]}"))
    }

    @Test
    fun `already closed ring is not duplicated`() {
        val zones = listOf(DeliveryZone("z-1", "Square", 100, square))

        val geojson = ZoneGeometry.toFeatureCollection(zones)

        val occurrences = Regex(Regex.escape("[77.0,12.0]")).findAll(geojson).count()
        assertEquals("first corner appears once as start and once as the closure", 2, occurrences)
    }

    @Test
    fun `no zones still produces valid geojson`() {
        assertEquals(
            """{"type":"FeatureCollection","features":[]}""",
            ZoneGeometry.toFeatureCollection(emptyList())
        )
    }

    @Test
    fun `label text cannot break out of the json string`() {
        val zones = listOf(
            DeliveryZone("z-1", "North \"gate\" \\ belt\nsecond line", 10, square)
        )

        val geojson = ZoneGeometry.toFeatureCollection(zones)

        assertTrue(geojson.contains("\\\"gate\\\""))
        assertTrue(geojson.contains("\\\\ belt"))
        assertTrue(geojson.contains("\\nsecond line"))
        // Re-parsing proves the escaping produced well-formed JSON.
        val parsed = element(geojson) as kotlinx.serialization.json.JsonObject
        val features = parsed["features"] as kotlinx.serialization.json.JsonArray
        val properties = (features.first() as kotlinx.serialization.json.JsonObject)["properties"]
        assertEquals(
            "North \"gate\" \\ belt\nsecond line",
            (properties as kotlinx.serialization.json.JsonObject)["label"]
                ?.let { (it as kotlinx.serialization.json.JsonPrimitive).content }
        )
    }

    @Test
    fun `centre of a ring is its bounding box middle`() {
        assertEquals(GeoPoint(12.5, 77.5), ZoneGeometry.centreOf(square))
        assertNull(ZoneGeometry.centreOf(emptyList()))
    }
}
