package com.balaji.provision.data.repository

import com.balaji.provision.data.location.GeoPoint
import com.balaji.provision.data.network.DeliveryAvailabilityRequest
import com.balaji.provision.data.network.DeliveryAvailabilityResponse
import com.balaji.provision.data.network.DeliveryZoneResponse
import com.balaji.provision.data.network.SupabaseApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import retrofit2.Response

/**
 * The published map is only useful if a deactivated or malformed area never reaches the
 * customer's screen, and the spot check is only honest if the coordinates really travel.
 */
class DeliveryAreaRepositoryTest {

    private val api: SupabaseApi = mock(SupabaseApi::class.java)
    private val repository = DeliveryAreaRepository(api)

    private val squareJson = Json.parseToJsonElement(
        """{"type":"Polygon","coordinates":[[[77,12],[78,12],[78,13],[77,13],[77,12]]]}"""
    ) as JsonObject

    private fun row(
        id: String,
        label: String = "Area",
        priority: Int = 100,
        active: Boolean = true,
        geojson: kotlinx.serialization.json.JsonObject? = squareJson
    ) = DeliveryZoneResponse(
        id = id,
        label = label,
        priority = priority,
        active = active,
        geojson = geojson
    )

    @Test
    fun `deactivated and unparseable areas are not drawn`() = runTest {
        `when`(api.getDeliveryZoneMap(anyString(), anyString(), anyString(), anyString(), anyInt()))
            .thenReturn(
                Response.success(
                    listOf(
                        row("ok", "Served belt", priority = 20),
                        row("off", "Closed belt", priority = 5, active = false),
                        row("bad", "Broken shape", priority = 1, geojson = null)
                    )
                )
            )

        val areas = repository.loadZones()

        val loaded = areas as DeliveryAreas.Loaded
        assertEquals(listOf("ok"), loaded.zones.map { it.id })
    }

    @Test
    fun `areas are ordered so the drawn outline matches the server's tie-break`() = runTest {
        `when`(api.getDeliveryZoneMap(anyString(), anyString(), anyString(), anyString(), anyInt()))
            .thenReturn(
                Response.success(
                    listOf(row("later", priority = 50), row("sooner", priority = 10))
                )
            )

        val loaded = repository.loadZones() as DeliveryAreas.Loaded

        assertEquals(listOf("sooner", "later"), loaded.zones.map { it.id })
    }

    @Test
    fun `a failed map read leaves the picker usable`() = runTest {
        `when`(api.getDeliveryZoneMap(anyString(), anyString(), anyString(), anyString(), anyInt()))
            .thenReturn(
                Response.error(500, "{}".toResponseBody("application/json".toMediaType()))
            )

        assertEquals(DeliveryAreas.Unavailable, repository.loadZones())
    }

    @Test
    fun `a blank pin never reaches the network`() = runTest {
        val result = repository.availability("   ", GeoPoint(12.9, 77.6))

        assertTrue(result.isFailure)
        verifyNoInteractions(api)
    }

    @Test
    fun `the spot the customer is pointing at is sent to the server`() = runTest {
        val captured = argumentCaptor<DeliveryAvailabilityRequest>()
        `when`(api.checkDeliveryAvailability(anyString(), anyString(), anyString(), captured.capture()))
            .thenReturn(Response.success(DeliveryAvailabilityResponse(deliverable = true)))

        repository.availability("247232", GeoPoint(12.9716, 77.5946), 10_000L)

        val body = captured.firstValue
        assertEquals("247232", body.p_pin_code)
        assertEquals(12.9716, body.p_lat!!, 1e-9)
        assertEquals(77.5946, body.p_lng!!, 1e-9)
        assertEquals(10_000L, body.p_eligible_subtotal)
    }

    @Test
    fun `no fix means no coordinates, not a zero at null island`() = runTest {
        val captured = argumentCaptor<DeliveryAvailabilityRequest>()
        `when`(api.checkDeliveryAvailability(anyString(), anyString(), anyString(), captured.capture()))
            .thenReturn(Response.success(DeliveryAvailabilityResponse(deliverable = true)))

        repository.availability("247232", null)

        assertNull(captured.firstValue.p_lat)
        assertNull(captured.firstValue.p_lng)
    }

    @Test
    fun `the server answer is carried through unchanged`() = runTest {
        `when`(api.checkDeliveryAvailability(anyString(), anyString(), anyString(), any()))
            .thenReturn(
                Response.success(
                    DeliveryAvailabilityResponse(
                        deliverable = true,
                        zone_id = "zone-9",
                        zone_label = "Inner belt",
                        fee_paise = 700L,
                        below_charge_threshold = true,
                        amount_to_free_delivery = 40_000L,
                        message = "We deliver here - Inner belt."
                    )
                )
            )

        val result = repository.availability("247232", GeoPoint(12.9, 77.6)).getOrThrow()

        assertEquals(true, result.deliverable)
        assertEquals("zone-9", result.zoneId)
        assertEquals("Inner belt", result.zoneLabel)
        assertEquals(700L, result.feePaise)
        assertEquals(40_000L, result.amountToFreeDeliveryPaise)
        assertEquals("We deliver here - Inner belt.", result.message)
    }

    @Test
    fun `an out-of-area answer is not turned into a success`() = runTest {
        `when`(api.checkDeliveryAvailability(anyString(), anyString(), anyString(), any()))
            .thenReturn(
                Response.success(
                    DeliveryAvailabilityResponse(
                        deliverable = false,
                        message = "We do not deliver to 999999 yet."
                    )
                )
            )

        val result = repository.availability("999999", GeoPoint(12.9, 77.6)).getOrThrow()

        assertEquals(false, result.deliverable)
        assertEquals("We do not deliver to 999999 yet.", result.message)
    }

    @Test
    fun `a server refusal surfaces its own reason`() = runTest {
        `when`(api.checkDeliveryAvailability(anyString(), anyString(), anyString(), any()))
            .thenReturn(
                Response.success(
                    DeliveryAvailabilityResponse(
                        error = "VALIDATION_FAILED",
                        message = "A PIN code is required to check delivery."
                    )
                )
            )

        val failure = repository.availability("122001", null)

        assertTrue(failure.isFailure)
        assertNotNull(failure.exceptionOrNull()?.message)
    }

    @Test
    fun `a transport error becomes a friendly failure, not a crash`() = runTest {
        `when`(api.checkDeliveryAvailability(anyString(), anyString(), anyString(), any()))
            .thenThrow(RuntimeException("network down"))

        val failure = repository.availability("122001", GeoPoint(12.9, 77.6))

        assertTrue(failure.isFailure)
        assertEquals(
            "Could not check delivery for this spot. Check your connection.",
            failure.exceptionOrNull()?.message
        )
    }
}
