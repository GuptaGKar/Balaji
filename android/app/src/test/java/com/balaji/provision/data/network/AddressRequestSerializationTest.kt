package com.balaji.provision.data.network

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressRequestSerializationTest {

    @Test
    fun `new address request keeps p id null so postgrest receives the create argument`() {
        val json = Json { encodeDefaults = false }
        val body = json.encodeToString(
            SaveMyAddressRequest(
                p_id = null,
                p_label = "Home",
                p_recipient_name = "Asha",
                p_phone = "9876543210",
                p_address_lines = "12, MG Road",
                p_landmark = null,
                p_area = null,
                p_city = "Pune",
                p_state = "Maharashtra",
                p_postal_code = "411005",
                p_is_default = true
            )
        )

        assertTrue(body.contains("\"p_id\":null"))
    }

    @Test
    fun `address request serializes coordinates when attached`() {
        val json = Json { encodeDefaults = false }
        val body = json.encodeToString(
            SaveMyAddressRequest(
                p_id = null,
                p_label = "Home",
                p_recipient_name = "Asha",
                p_phone = "9876543210",
                p_address_lines = "12, MG Road",
                p_landmark = null,
                p_area = null,
                p_city = "Pune",
                p_state = "Maharashtra",
                p_postal_code = "411005",
                p_is_default = true,
                p_lat = 18.5204,
                p_lng = 73.8567
            )
        )

        assertTrue(body.contains("\"p_lat\":18.5204"))
        assertTrue(body.contains("\"p_lng\":73.8567"))
    }
}
