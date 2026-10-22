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
            isDefault = true
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
