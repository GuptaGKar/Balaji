package com.balaji.provision.ui.account

import com.balaji.provision.data.location.DeliveryZone
import com.balaji.provision.data.location.GeoPoint
import com.balaji.provision.data.location.LocationFixSource
import com.balaji.provision.data.repository.DeliveryAreaSource
import com.balaji.provision.data.repository.DeliveryAreas
import com.balaji.provision.data.repository.DeliveryAvailability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The picker's contract: drag once, ask once, ask about the newest spot, and never let a
 * missing answer stop the customer from saving the address.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddressMapPickerViewModelTest {

    private class FakeAreas : DeliveryAreaSource {
        var zones: DeliveryAreas = DeliveryAreas.Loaded(emptyList())
        var loadCalls = 0
        val asked = mutableListOf<Pair<String, GeoPoint?>>()
        var responder: (String, GeoPoint?) -> Result<DeliveryAvailability> = { _, _ ->
            Result.success(deliverable(zoneId = null, zoneLabel = null))
        }

        override suspend fun loadZones(): DeliveryAreas {
            loadCalls++
            return zones
        }

        override suspend fun availability(
            pin: String,
            point: GeoPoint?,
            subtotalPaise: Long
        ): Result<DeliveryAvailability> {
            asked += pin to point
            return responder(pin, point)
        }
    }

    private class FakeFix : LocationFixSource {
        var permitted = true
        var next: Result<GeoPoint> = Result.success(GeoPoint(12.97, 77.59))
        var calls = 0

        override fun hasPermission(): Boolean = permitted

        override suspend fun currentLocation(): Result<GeoPoint> {
            calls++
            return next
        }
    }

    private val dispatcher = StandardTestDispatcher()
    private val areas = FakeAreas()
    private val fix = FakeFix()
    private lateinit var viewModel: AddressMapPickerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = AddressMapPickerViewModel(areas, fix)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `opening on a saved spot centres the camera and asks about it`() = runTest {
        areas.zones = DeliveryAreas.Loaded(listOf(zone("z-1", "Inner belt")))

        viewModel.openAt(point = GeoPoint(12.97, 77.59), pin = "247232")
        advanceTimeBy(600)
        runCurrent()

        val state = viewModel.state.value
        assertEquals(GeoPoint(12.97, 77.59), state.point)
        assertEquals(GeoPoint(12.97, 77.59), state.cameraTarget)
        assertEquals(1L, state.cameraRequestId)
        assertEquals(1, state.zones.size)
        assertEquals(listOf("247232" to GeoPoint(12.97, 77.59)), areas.asked)
        assertTrue(state.answered?.deliverable == true)
    }

    @Test
    fun `a burst of drag updates asks the server once about the last spot`() = runTest {
        viewModel.openAt(point = null, pin = "247232")

        viewModel.onCameraIdle(GeoPoint(12.0, 77.0), pin = "247232")
        viewModel.onCameraIdle(GeoPoint(12.5, 77.4), pin = "247232")
        viewModel.onCameraIdle(GeoPoint(12.9, 77.6), pin = "247232")
        advanceTimeBy(600)
        runCurrent()

        assertEquals("debounce must collapse the drag", listOf(GeoPoint(12.9, 77.6)), areas.asked.map { it.second })
        assertEquals(GeoPoint(12.9, 77.6), viewModel.state.value.point)
    }

    @Test
    fun `an unanswered spot is still held while the check is in flight`() = runTest {
        viewModel.openAt(point = GeoPoint(12.9, 77.6), pin = "247232")

        // The debounce has not elapsed yet, so nothing has been asked.
        assertTrue(areas.asked.isEmpty())
        viewModel.onCameraIdle(GeoPoint(13.0, 77.7), pin = "247232")
        assertEquals(
            "the newest spot is the one being held",
            SpotCheck.Checking,
            viewModel.state.value.check
        )
    }

    @Test
    fun `opening with no saved spot invents nothing`() = runTest {
        // A new address has no coordinates. The picker must not claim a spot - the map
        // control's own default used to arrive here as (0,0) in the Atlantic.
        viewModel.openAt(point = null, pin = "247232")
        advanceTimeBy(1_000)
        runCurrent()

        assertNull(viewModel.state.value.point)
        assertNull(viewModel.state.value.cameraTarget)
        assertEquals(SpotCheck.Idle, viewModel.state.value.check)
        assertTrue(areas.asked.isEmpty())
    }

    @Test
    fun `a blank pin is not sent to the server`() = runTest {
        viewModel.openAt(point = GeoPoint(12.9, 77.6), pin = "   ")
        advanceTimeBy(1_000)
        runCurrent()

        assertTrue(areas.asked.isEmpty())
        assertEquals(SpotCheck.Idle, viewModel.state.value.check)
        assertNotNull(viewModel.state.value.notice)
    }

    @Test
    fun `a failed check reports itself without losing the chosen spot`() = runTest {
        areas.responder = { _, _ -> Result.failure(IllegalStateException("Offline")) }

        viewModel.openAt(point = GeoPoint(12.9, 77.6), pin = "247232")
        advanceTimeBy(600)
        runCurrent()

        val state = viewModel.state.value
        assertEquals(GeoPoint(12.9, 77.6), state.point)
        assertTrue(state.check is SpotCheck.Failed)
        assertEquals("Offline", (state.check as SpotCheck.Failed).message)
    }

    @Test
    fun `recenter moves the camera to the fix and asks about it`() = runTest {
        fix.next = Result.success(GeoPoint(12.11, 77.22))

        viewModel.useMyLocation(pin = "247232")
        runCurrent()
        advanceTimeBy(600)
        runCurrent()

        assertEquals(1, fix.calls)
        assertEquals(GeoPoint(12.11, 77.22), viewModel.state.value.cameraTarget)
        assertEquals(GeoPoint(12.11, 77.22), viewModel.state.value.point)
        assertEquals(listOf(GeoPoint(12.11, 77.22)), areas.asked.map { it.second })
        assertEquals(false, viewModel.state.value.isLocating)
    }

    @Test
    fun `a missing fix surfaces the reason instead of silently doing nothing`() = runTest {
        fix.next = Result.failure(IllegalStateException("No GPS fix near a window"))

        viewModel.useMyLocation(pin = "247232")
        runCurrent()

        assertEquals("No GPS fix near a window", viewModel.state.value.notice)
        assertEquals(false, viewModel.state.value.isLocating)
        assertNull(viewModel.state.value.point)
        assertTrue(areas.asked.isEmpty())
    }

    @Test
    fun `changing the pin re-asks about the same spot`() = runTest {
        viewModel.openAt(point = GeoPoint(12.9, 77.6), pin = "247232")
        advanceTimeBy(600)
        runCurrent()

        viewModel.recheck(pin = "122001")
        advanceTimeBy(600)
        runCurrent()

        assertEquals(listOf("247232", "122001"), areas.asked.map { it.first })
        assertEquals(GeoPoint(12.9, 77.6), viewModel.state.value.point)
    }

    @Test
    fun `reset forgets the spot but keeps the published map`() = runTest {
        areas.zones = DeliveryAreas.Loaded(listOf(zone("z-1", "Inner belt")))
        viewModel.openAt(point = GeoPoint(12.9, 77.6), pin = "247232")
        advanceTimeBy(600)
        runCurrent()

        viewModel.reset()
        runCurrent()

        val state = viewModel.state.value
        assertNull(state.point)
        assertEquals(SpotCheck.Idle, state.check)
        assertEquals("the area map belongs to the store, not the address", 1, state.zones.size)

        // Re-opening for the next address must reuse the published map, not refetch it.
        viewModel.openAt(point = GeoPoint(13.2, 77.6), pin = "122001")
        runCurrent()
        assertEquals("zones are not refetched for every address", 1, areas.loadCalls)
    }

    @Test
    fun `unavailable areas still leave the map usable`() = runTest {
        areas.zones = DeliveryAreas.Unavailable

        viewModel.openAt(point = GeoPoint(12.9, 77.6), pin = "247232")
        runCurrent()

        val state = viewModel.state.value
        assertTrue(state.zones.isEmpty())
        assertTrue(state.zonesLoaded)
        assertNotNull(state.notice)
        // The spot check is independent of the overlay and still runs.
        advanceTimeBy(600)
        runCurrent()
        assertEquals(1, areas.asked.size)
    }

    private fun zone(id: String, label: String) = DeliveryZone(
        id = id,
        label = label,
        priority = 10,
        ring = listOf(
            GeoPoint(12.0, 77.0), GeoPoint(12.0, 78.0),
            GeoPoint(13.0, 78.0), GeoPoint(13.0, 77.0)
        )
    )

}

/** A served spot, as the server would describe it. */
private fun deliverable(zoneId: String?, zoneLabel: String?) = DeliveryAvailability(
    deliverable = true,
    feePaise = 700L,
    zoneId = zoneId,
    zoneLabel = zoneLabel,
    ruleNote = "Delivery fee for zone applied",
    belowChargeThreshold = true,
    amountToFreeDeliveryPaise = 40_000L,
    message = "We deliver here."
)
