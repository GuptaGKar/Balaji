package com.balaji.provision.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AddressDraftTest {

    @Test
    fun `valid draft normalizes indian mobile number`() {
        val draft = validDraft().copy(phone = "+91 98765-43210")

        assertNull(draft.validate())
        assertEquals("9876543210", draft.normalizedPhone())
    }

    @Test
    fun `draft rejects missing required fields`() {
        val draft = validDraft().copy(
            recipientName = " ",
            addressLines = "",
            city = "",
            state = "",
            postalCode = "123"
        )

        assertEquals("Enter the recipient name.", draft.validate())
        assertNotNull(draft.validate())
    }

    @Test
    fun `draft rejects invalid phone`() {
        val draft = validDraft().copy(phone = "12345")

        assertEquals("Enter a valid 10-digit mobile number.", draft.validate())
    }

    @Test
    fun `draft rejects invalid pin code`() {
        val draft = validDraft().copy(postalCode = "12345A")

        assertEquals("Enter a valid 6-digit PIN code.", draft.validate())
    }

    @Test
    fun `draft rejects a partial location pair`() {
        val draft = validDraft().copy(lat = 12.9716)

        assertEquals("Location needs both latitude and longitude.", draft.validate())
    }

    @Test
    fun `draft rejects out-of-range coordinates`() {
        val draft = validDraft().copy(lat = 91.0, lng = 77.5946)

        assertEquals("Invalid location coordinates.", draft.validate())
    }

    @Test
    fun `draft accepts valid coordinates`() {
        val draft = validDraft().copy(lat = 12.9716, lng = 77.5946)

        assertNull(draft.validate())
    }

    @Test
    fun `draft rejects the null island a device reports with no fix`() {
        // (0,0) passes a range test, so it needs its own rule: it is never a doorstep.
        val draft = validDraft().copy(lat = 0.0, lng = 0.0)

        assertEquals(
            "That location is not usable. Pick a spot on the map.",
            draft.validate()
        )
    }

    @Test
    fun `draft rejects non-finite coordinates`() {
        // NaN and Infinity compare false against every range bound, so without an
        // explicit test they would be stored as a plausible address.
        assertEquals(
            "Invalid location coordinates.",
            validDraft().copy(lat = Double.NaN, lng = 77.5946).validate()
        )
        assertEquals(
            "Invalid location coordinates.",
            validDraft().copy(lat = 12.9716, lng = Double.POSITIVE_INFINITY).validate()
        )
    }

    @Test
    fun `draft still allows a real coordinate close to zero`() {
        // The (0,0) rule must not reject the equator or the Greenwich meridian itself.
        assertNull(validDraft().copy(lat = 0.0, lng = 77.5946).validate())
        assertNull(validDraft().copy(lat = 12.9716, lng = 0.0).validate())
    }

    @Test
    fun `saved address builds a complete display address`() {
        val address = SavedAddress(
            id = "address-1",
            label = "Home",
            recipientName = "Asha",
            phone = "9876543210",
            addressLines = "12, MG Road",
            landmark = "Near temple",
            area = "Shivaji Nagar",
            city = "Pune",
            state = "Maharashtra",
            postalCode = "411005",
            isDefault = true,
            lat = 18.5204,
            lng = 73.8567
        )

        assertEquals(
            "12, MG Road, Near temple, Shivaji Nagar, Pune, Maharashtra - 411005",
            address.fullAddress()
        )
    }

    private fun validDraft() = AddressDraft(
        label = "Home",
        recipientName = "Asha",
        phone = "9876543210",
        addressLines = "12, MG Road",
        landmark = "Near temple",
        area = "Shivaji Nagar",
        city = "Pune",
        state = "Maharashtra",
        postalCode = "411005",
        isDefault = true
    )
}
