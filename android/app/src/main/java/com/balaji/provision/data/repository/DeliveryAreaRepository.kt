package com.balaji.provision.data.repository

import com.balaji.provision.BuildConfig
import com.balaji.provision.data.location.DeliveryZone
import com.balaji.provision.data.location.GeoPoint
import com.balaji.provision.data.location.ZoneGeometry
import com.balaji.provision.data.network.DeliveryAvailabilityRequest
import com.balaji.provision.data.network.DeliveryAvailabilityResponse
import com.balaji.provision.data.network.SupabaseApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The server's answer about one candidate drop point. Everything here is copied from
 * `check_delivery_availability`; nothing is derived on the device, so the banner cannot
 * drift from what the order will actually be charged.
 */
data class DeliveryAvailability(
    val deliverable: Boolean,
    val feePaise: Long,
    val zoneId: String?,
    val zoneLabel: String?,
    val ruleNote: String,
    val belowChargeThreshold: Boolean,
    val amountToFreeDeliveryPaise: Long,
    val message: String
)

/** Loaded zones are drawable; a failure simply means the map shows no overlay. */
sealed interface DeliveryAreas {
    data class Loaded(val zones: List<DeliveryZone>) : DeliveryAreas
    data object Unavailable : DeliveryAreas
}

/**
 * Seam over the published delivery map. The pin picker depends on this rather than on
 * the Retrofit interface, so its debouncing and stale-answer rules are testable.
 */
interface DeliveryAreaSource {
    suspend fun loadZones(): DeliveryAreas

    suspend fun availability(
        pin: String,
        point: GeoPoint?,
        subtotalPaise: Long = 0L
    ): Result<DeliveryAvailability>
}

@Singleton
class DeliveryAreaRepository @Inject constructor(
    private val api: SupabaseApi
) : DeliveryAreaSource {
    /**
     * Both the map view and the availability RPC are granted to `anon`, so the pin
     * picker works during onboarding, before any session exists.
     */
    private val anonKey: String get() = BuildConfig.SUPABASE_ANON_KEY

    override suspend fun loadZones(): DeliveryAreas {
        return try {
            val response = api.getDeliveryZoneMap(
                apiKey = anonKey,
                auth = "Bearer $anonKey"
            )
            if (!response.isSuccessful) return DeliveryAreas.Unavailable
            val zones = response.body().orEmpty()
                .filter { it.active }
                .mapNotNull { row ->
                    val ring = ZoneGeometry.polygonRing(row.geojson) ?: return@mapNotNull null
                    DeliveryZone(
                        id = row.id,
                        label = row.label.ifBlank { row.id },
                        priority = row.priority,
                        ring = ring
                    )
                }
                .sortedBy { it.priority }
            DeliveryAreas.Loaded(zones)
        } catch (_: Exception) {
            DeliveryAreas.Unavailable
        }
    }

    /**
     * Null pin/blank pin is not worth a round trip: the RPC would only answer
     * VALIDATION_FAILED. Callers keep that case out of the network entirely.
     */
    override suspend fun availability(
        pin: String,
        point: GeoPoint?,
        subtotalPaise: Long
    ): Result<DeliveryAvailability> {
        val cleanPin = pin.trim()
        if (cleanPin.isBlank()) {
            return Result.failure(
                IllegalArgumentException("Enter the PIN code to check this spot.")
            )
        }
        return try {
            val response = api.checkDeliveryAvailability(
                apiKey = anonKey,
                auth = "Bearer $anonKey",
                body = DeliveryAvailabilityRequest(
                    p_pin_code = cleanPin,
                    p_lat = point?.latitude,
                    p_lng = point?.longitude,
                    p_eligible_subtotal = subtotalPaise
                )
            )
            if (!response.isSuccessful) {
                return Result.failure(
                    IllegalStateException(COULD_NOT_CHECK)
                )
            }
            val body = response.body()
                ?: return Result.failure(IllegalStateException(COULD_NOT_CHECK))
            if (!body.error.isNullOrBlank()) {
                return Result.failure(
                    IllegalStateException(body.message ?: COULD_NOT_CHECK)
                )
            }
            Result.success(body.toDomain())
        } catch (_: Exception) {
            Result.failure(IllegalStateException(COULD_NOT_CHECK))
        }
    }

    private fun DeliveryAvailabilityResponse.toDomain() = DeliveryAvailability(
        deliverable = deliverable,
        feePaise = fee_paise,
        zoneId = zone_id?.takeIf { it.isNotBlank() },
        zoneLabel = zone_label?.takeIf { it.isNotBlank() },
        ruleNote = rule_note.orEmpty(),
        belowChargeThreshold = below_charge_threshold,
        amountToFreeDeliveryPaise = amount_to_free_delivery,
        message = message.orEmpty()
    )

    private companion object {
        const val COULD_NOT_CHECK =
            "Could not check delivery for this spot. Check your connection."
    }
}
