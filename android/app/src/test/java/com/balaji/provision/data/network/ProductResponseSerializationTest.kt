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

    // ---- multi-category membership (migration 20261105000001) ----
    //
    // categoryIds is what listings and category search read. If it ever came back empty on an
    // un-migrated database, every product would vanish from every category listing - so the
    // primary-only fallback is the behaviour these tests exist to pin.

    private fun payloadWithMembership(category: String, membership: String) = """
        [{"id":"synthetic-product","sku":"QA-PACK","name":"Synthetic pack",
          "category_id":$category,"quantity_type":"PACKED","price_paise":1000,
          "price_basis_qty":1,"unit_label":"pack","step_qty":1,"min_qty":1,
          "stock_qty":5,"low_stock_threshold":2,"active":true,"available":true,
          "product_categories":$membership}]
    """.trimIndent()

    @Test
    fun `embedded membership becomes the category set`() {
        val product = Json
            .decodeFromString<List<ProductResponse>>(
                payloadWithMembership("\"cat-primary\"", """[{"category_id":"cat-extra"}]""")
            )
            .single()
            .toDomain()
        assertEquals("cat-primary", product.categoryId)
        assertEquals(setOf("cat-primary", "cat-extra"), product.categoryIds)
    }

    @Test
    fun `the primary is never duplicated when the server already lists it as a member`() {
        val product = Json
            .decodeFromString<List<ProductResponse>>(
                payloadWithMembership("\"cat-primary\"", """[{"category_id":"cat-primary"}]""")
            )
            .single()
            .toDomain()
        assertEquals(setOf("cat-primary"), product.categoryIds)
    }

    @Test
    fun `a missing embed falls back to the primary alone, so an unmigrated store still lists products`() {
        val product = Json.decodeFromString<List<ProductResponse>>(payload("\"cat-primary\""))
            .single()
            .toDomain()
        assertEquals(setOf("cat-primary"), product.categoryIds)
    }

    @Test
    fun `a null category id inside the embed is skipped rather than added as an empty string`() {
        val product = Json
            .decodeFromString<List<ProductResponse>>(
                payloadWithMembership("\"cat-primary\"", """[{"category_id":null},{"category_id":"cat-ok"}]""")
            )
            .single()
            .toDomain()
        assertEquals(setOf("cat-primary", "cat-ok"), product.categoryIds)
    }

    @Test
    fun `an uncategorized product with no membership ends up with an empty set, not a blank id`() {
        val product = Json.decodeFromString<List<ProductResponse>>(payloadWithMembership("null", "[]"))
            .single()
            .toDomain()
        assertEquals("", product.categoryId)
        assertEquals(emptySet<String>(), product.categoryIds)
    }
}
