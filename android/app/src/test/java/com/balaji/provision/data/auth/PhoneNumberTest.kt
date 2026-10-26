package com.balaji.provision.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneNumberTest {

    @Test
    fun `accepts ten digit Indian mobile`() {
        assertEquals("+919876543210", PhoneNumber.normalizeIndian("9876543210"))
    }

    @Test
    fun `accepts country code and separators`() {
        assertEquals("+919876543210", PhoneNumber.normalizeIndian("+91 98765-43210"))
        assertEquals("+919876543210", PhoneNumber.normalizeIndian("919876543210"))
    }

    @Test
    fun `accepts leading zero`() {
        assertEquals("+919876543210", PhoneNumber.normalizeIndian("09876543210"))
    }

    @Test
    fun `rejects landline and malformed numbers`() {
        assertNull(PhoneNumber.normalizeIndian("02212345678"))
        assertNull(PhoneNumber.normalizeIndian("5876543210"))
        assertNull(PhoneNumber.normalizeIndian("98765"))
        assertNull(PhoneNumber.normalizeIndian(""))
        assertNull(PhoneNumber.normalizeIndian("+1 555 0100"))
    }
}
