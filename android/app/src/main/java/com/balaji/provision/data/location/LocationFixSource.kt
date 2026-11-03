package com.balaji.provision.data.location

/**
 * The part of location the delivery pin picker needs. Kept as a seam so the picker's
 * debouncing and stale-answer handling can be tested without an Android context or a
 * live GPS radio.
 */
interface LocationFixSource {
    fun hasPermission(): Boolean

    /** Success only when a usable fix exists; every other case is a failure to show. */
    suspend fun currentLocation(): Result<GeoPoint>
}
