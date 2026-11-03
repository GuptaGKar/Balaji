package com.balaji.provision.data.network

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ProductResponseSerializationTest {
    private fun payload(category: String) = """
        [{"id":"synthetic-product","sku":"QA-PACK","name":"Synthetic pack",
          "category_id":$category,"quantity_type":"PACKED","price_paise":1000,
          "price_basis_qty":1,"unit_label":"pack","step_qty":1,"min_qty":1,
          "stock_qty":5,"low_stock_threshold":2,"active":true,"available":true}]
    """.trimIndent()

    @Test
    fun `uncategorized product does not prevent the whole catalogue from loading`() {
        val products = Json.decodeFromString<List<ProductResponse>>(payload("null")).map { it.toDomain() }
        assertEquals(1, products.size)
        assertEquals("Synthetic pack", products.single().name)
        assertEquals("", products.single().categoryId)
        assertEquals(1000L, products.single().pricePaise)
    }

    @Test
    fun `categorized product retains its category`() {
        val product = Json.decodeFromString<List<ProductResponse>>(payload("\"synthetic-category\"")).single().toDomain()
        assertEquals("synthetic-category", product.categoryId)
    }
}
