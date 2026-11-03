package com.balaji.provision.data.location

import com.balaji.provision.domain.model.AddressDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M4 completion rules. The patch tests are the important ones: a completion that silently
 * overwrote something the customer already typed, or that invented a city we do not store,
 * would be a data-integrity bug in the address book rather than a UI glitch.
 */
class AreaSuggestionTest {

    private val emptyDraft = AddressDraft(
        label = "Home",
        recipientName = "",
        phone = "",
        addressLines = "",
        landmark = "",
        area = "",
        city = "",
        state = "",
        postalCode = "",
        isDefault = true
    )

    private fun area(kind: String, value: String, label: String, point: GeoPoint? = null) =
        AreaSuggestion(
            type = SuggestionType.AREA, id = "area-1", label = label,
            kind = kind, value = value, point = point
        )

    private fun savedAddress(point: GeoPoint? = null) = AreaSuggestion(
        type = SuggestionType.SAVED_ADDRESS, id = "addr-1", label = "Work",
        recipientName = "Ravi", phone = "9876543210", addressLines = "42 MG Road",
        landmark = "Opposite metro", area = "Indiranagar", city = "Bengaluru",
        state = "Karnataka", postalCode = "560038", point = point
    )

    @Test
    fun `searching starts at two characters and stops being sensible at sixty-five`() {
        assertFalse(suggestionQueryIsSearchable(""))
        assertFalse(suggestionQueryIsSearchable("   "))
        assertFalse(suggestionQueryIsSearchable("q"))
        assertTrue(suggestionQueryIsSearchable("qa"))
        assertTrue(suggestionQueryIsSearchable("  kor  "))
        assertFalse("a pasted wall of text is not a search", suggestionQueryIsSearchable("z".repeat(65)))
    }

    @Test
    fun `a pin-code area fills both the area name and the PIN`() {
        val patched = area("pincode", "560038", "Indiranagar").patchOnto(emptyDraft)
        assertEquals("Indiranagar", patched.area)
        assertEquals("560038", patched.postalCode)
    }

    @Test
    fun `a neighbourhood area never invents a PIN the customer did not type`() {
        // The row is a name, not a PIN. Guessing one would put a wrong number in the field the
        // server actually gates delivery on.
        val patched = area("neighbourhood", "Koramangala", "Koramangala 5th Block").patchOnto(emptyDraft)
        assertEquals("", patched.postalCode)
        assertEquals("Koramangala 5th Block", patched.area)
    }

    @Test
    fun `an area chip leaves city and state alone because service_areas does not store them`() {
        val patched = area("pincode", "560038", "Indiranagar").patchOnto(emptyDraft)
        assertEquals("", patched.city)
        assertEquals("", patched.state)
    }

    @Test
    fun `a saved address chip fills the address fields it actually has`() {
        val patched = savedAddress().patchOnto(emptyDraft)
        assertEquals("Ravi", patched.recipientName)
        assertEquals("9876543210", patched.phone)
        assertEquals("42 MG Road", patched.addressLines)
        assertEquals("Opposite metro", patched.landmark)
        assertEquals("Indiranagar", patched.area)
        assertEquals("Bengaluru", patched.city)
        assertEquals("Karnataka", patched.state)
        assertEquals("560038", patched.postalCode)
    }

    @Test
    fun `an empty field in a suggestion keeps what the customer already typed`() {
        val typed = emptyDraft.copy(city = "Manual City", postalCode = "122001")
        val sparse = AreaSuggestion(
            type = SuggestionType.SAVED_ADDRESS, id = "addr-2", label = "Home",
            city = "", postalCode = "   ", recipientName = null
        )
        val patched = sparse.patchOnto(typed)
        assertEquals("Manual City", patched.city)
        assertEquals("122001", patched.postalCode)
        assertEquals("", patched.recipientName)
    }

    @Test
    fun `the default flag survives a completion`() {
        assertTrue(area("pincode", "560038", "Indiranagar").patchOnto(emptyDraft).isDefault)
    }

    @Test
    fun `a chip with a location moves the map and one without leaves the dropped pin intact`() {
        val withPoint = area("pincode", "560038", "Indiranagar", GeoPoint(12.97, 77.59))
            .patchOnto(emptyDraft)
        assertEquals(12.97, withPoint.lat!!, 0.0001)
        assertEquals(77.59, withPoint.lng!!, 0.0001)

        val alreadyDropped = emptyDraft.copy(lat = 28.61, lng = 77.21)
        val unlocated = savedAddress().patchOnto(alreadyDropped)
        assertEquals(28.61, unlocated.lat!!, 0.0001)
        assertEquals(77.21, unlocated.lng!!, 0.0001)
    }

    @Test
    fun `a suggestion with no coordinates keeps the pin already dropped`() {
        val unlocated = AreaSuggestion(
            type = SuggestionType.SAVED_ADDRESS, id = "addr-3", label = "Home", point = null
        )
        val patched = unlocated.patchOnto(emptyDraft.copy(lat = 1.5, lng = 2.5))
        assertEquals(1.5, patched.lat!!, 0.0001)
        assertEquals(2.5, patched.lng!!, 0.0001)
    }

    @Test
    fun `chip label joins the detail the mapping supplied, and adds nothing when there is none`() {
        // The "PIN 560038" part is composed by the repository mapping (asserted there); this
        // function's only job is to join the two cleanly.
        val withDetail = AreaSuggestion(
            type = SuggestionType.AREA, id = "area-9", label = "Indiranagar", sublabel = "PIN 560038"
        )
        assertEquals("Indiranagar · PIN 560038", withDetail.chipLabel())
        assertEquals("Indiranagar", area("pincode", "560038", "Indiranagar").chipLabel())
    }
}
