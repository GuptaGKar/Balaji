package com.balaji.provision.data.location

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A service area that carries a boundary, as delivered by the draw-only
 * `delivery_zone_map` view. Amounts are deliberately absent: that view publishes no
 * fee columns, and the client must never compute a price.
 */
data class DeliveryZone(
    val id: String,
    val label: String,
    val priority: Int,
    val ring: List<GeoPoint>
) {
    /** True when the point is inside this zone's outline. Display only. */
    fun covers(point: GeoPoint): Boolean = ZoneGeometry.contains(ring, point)
}

/**
 * Geometry helpers for the delivery-area overlay.
 *
 * Everything here exists to draw and to highlight. Deliverability and pricing are
 * decided by `check_delivery_availability` and `compute_delivery_fee_for_pin` on the
 * server; a client-side polygon test must never be used to accept an order or to show
 * a price, because the polygon it works from is only a copy of the published map.
 */
object ZoneGeometry {

    /**
     * Parse a GeoJSON `Polygon` into its exterior ring.
     * Returns null for anything that is not a usable polygon (wrong type, missing or
     * non-numeric coordinates, or fewer than three distinct corners), so a malformed row
     * from the view is skipped rather than drawn as a spike. A closed ring carries four
     * coordinates for three corners, which is the floor accepted here.
     */
    fun polygonRing(element: JsonElement?): List<GeoPoint>? {
        val polygon = element as? JsonObject ?: return null
        if ((polygon["type"] as? JsonPrimitive)?.content != "Polygon") return null
        val rings = polygon["coordinates"] as? JsonArray ?: return null
        val outer = rings.firstOrNull() as? JsonArray ?: return null

        val points = outer.mapNotNull { coordinate ->
            val pair = coordinate as? JsonArray ?: return@mapNotNull null
            val lng = pair.getOrNull(0).asDoubleOrNull() ?: return@mapNotNull null
            val lat = pair.getOrNull(1).asDoubleOrNull() ?: return@mapNotNull null
            if (!lat.isFinite() || !lng.isFinite()) return@mapNotNull null
            GeoPoint(latitude = lat, longitude = lng)
        }
        return if (points.size >= MIN_RING_POINTS) points else null
    }

    /**
     * Even-odd ray casting. Uses the polygon's own coordinate space, so a point that
     * sits exactly on an edge is not reliably inside or outside - which is fine here,
     * because the server has the authoritative boundary and this only picks a colour.
     */
    fun contains(ring: List<GeoPoint>, point: GeoPoint): Boolean {
        if (ring.size < MIN_RING_POINTS) return false
        var inside = false
        var previous = ring.lastIndex
        for (current in ring.indices) {
            val a = ring[current]
            val b = ring[previous]
            val straddles = (a.latitude > point.latitude) != (b.latitude > point.latitude)
            if (straddles) {
                val dy = b.latitude - a.latitude
                if (dy == 0.0) {
                    previous = current
                    continue
                }
                val crossing = b.longitude -
                    ((b.longitude - a.longitude) * (point.latitude - b.latitude)) / dy
                if (point.longitude < crossing) inside = !inside
            }
            previous = current
        }
        return inside
    }

    /**
     * Serialise zones into the GeoJSON text a MapLibre source expects. Every zone gets
     * its id, label and priority as feature properties so the map style can pick the
     * colour of the area the customer is currently pointing at.
     */
    fun toFeatureCollection(zones: List<DeliveryZone>): String {
        if (zones.isEmpty()) return EMPTY_FEATURE_COLLECTION
        val features = zones.joinToString(separator = ",") { zone ->
            "{" +
                "\"type\":\"Feature\"," +
                "\"properties\":{" +
                "\"id\":${jsonString(zone.id)}," +
                "\"label\":${jsonString(zone.label)}," +
                "\"priority\":${zone.priority}" +
                "}," +
                "\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[${zone.ring.toRingJson()}]]}" +
                "}"
        }
        return "{\"type\":\"FeatureCollection\",\"features\":[$features]}"
    }

    /** `[lng,lat]` list for one linear ring, closed explicitly as GeoJSON requires. */
    private fun List<GeoPoint>.toRingJson(): String {
        if (isEmpty()) return ""
        val points = joinToString(separator = ",") { "[${it.longitude},${it.latitude}]" }
        val first = first()
        return if (first == last()) points else "$points,[${first.longitude},${first.latitude}]"
    }

    /** Centre of a ring, used to place the camera when a suggestion is tapped. */
    fun centreOf(ring: List<GeoPoint>): GeoPoint? {
        if (ring.isEmpty()) return null
        val lats = ring.map { it.latitude }
        val lngs = ring.map { it.longitude }
        return GeoPoint(
            latitude = (lats.min() + lats.max()) / 2.0,
            longitude = (lngs.min() + lngs.max()) / 2.0
        )
    }

    private fun JsonElement?.asDoubleOrNull(): Double? {
        val primitive = this as? JsonPrimitive ?: return null
        val text = primitive.content
        if (text.isEmpty() || text == "null") return null
        return text.toDoubleOrNull()
    }

    private fun jsonString(value: String): String =
        buildString {
            append('"')
            value.forEach { char ->
                when (char) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (char.code < 0x20) append(UnicodeEscape(char.code)) else append(char)
                }
            }
            append('"')
        }

    private data class UnicodeEscape(val code: Int) {
        override fun toString(): String = "\\u%04x".format(code)
    }

    private const val MIN_RING_POINTS = 4
    private const val EMPTY_FEATURE_COLLECTION = "{\"type\":\"FeatureCollection\",\"features\":[]}"
}
