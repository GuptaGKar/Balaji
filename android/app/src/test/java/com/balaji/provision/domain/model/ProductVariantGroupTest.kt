package com.balaji.provision.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductVariantGroupTest {

    private fun product(
        id: String,
        name: String,
        pricePaise: Long,
        variantGroupId: String? = null,
        variantLabel: String? = null,
        packSizeLabel: String? = null,
        stockQty: Long = 10,
        minQty: Long = 1,
        quantityType: QuantityType = QuantityType.PACKED,
        unitLabel: String = "pack",
        priceBasisQty: Long = 1,
        sku: String = "SKU-$id",
        active: Boolean = true,
        available: Boolean = true
    ) = Product(
        id = id,
        sku = sku,
        name = name,
        description = null,
        categoryId = "cat-1",
        imagePath = null,
        quantityType = quantityType,
        pricePaise = pricePaise,
        priceBasisQty = priceBasisQty,
        unitLabel = unitLabel,
        packSizeLabel = packSizeLabel,
        stepQty = 1,
        minQty = minQty,
        maxQty = null,
        stockQty = stockQty,
        lowStockThreshold = 3,
        active = active,
        available = available,
        variantGroupId = variantGroupId,
        variantLabel = variantLabel
    )

    @Test
    fun `two pack sizes of the same group become one group with two variants`() {
        val milk500 = product(
            id = "p1", name = "Amul Toned Milk", pricePaise = 2500,
            variantGroupId = "g-milk", variantLabel = "500 ml"
        )
        val milk1l = product(
            id = "p2", name = "Amul Toned Milk", pricePaise = 4800,
            variantGroupId = "g-milk", variantLabel = "1 L"
        )

        val groups = listOf(milk500, milk1l).toVariantGroups()

        assertEquals(1, groups.size)
        assertEquals("g-milk", groups[0].groupId)
        assertEquals(2, groups[0].variants.size)
        assertTrue(groups[0].hasMultipleVariants)
        assertEquals("500 ml · 1 L", groups[0].packSummary)
        // Default prefers the cheaper in-stock pack.
        assertEquals("p1", groups[0].defaultVariant.id)
    }

    @Test
    fun `default variant prefers in-stock over cheaper out-of-stock pack`() {
        val cheaperOut = product(
            id = "p1", name = "Rice", pricePaise = 10000,
            variantGroupId = "g-rice", variantLabel = "1 kg", stockQty = 0
        )
        val costlierIn = product(
            id = "p2", name = "Rice", pricePaise = 12000,
            variantGroupId = "g-rice", variantLabel = "2 kg", stockQty = 5
        )

        val groups = listOf(cheaperOut, costlierIn).toVariantGroups()

        assertEquals(1, groups.size)
        assertEquals("p2", groups[0].defaultVariant.id)
    }

    @Test
    fun `variants are ordered by price ascending`() {
        val big = product(
            id = "p3", name = "Oil", pricePaise = 9000,
            variantGroupId = "g-oil", variantLabel = "2 L"
        )
        val small = product(
            id = "p1", name = "Oil", pricePaise = 4500,
            variantGroupId = "g-oil", variantLabel = "1 L"
        )
        val mid = product(
            id = "p2", name = "Oil", pricePaise = 6800,
            variantGroupId = "g-oil", variantLabel = "1.5 L"
        )

        val groups = listOf(big, small, mid).toVariantGroups()

        assertEquals(listOf("p1", "p2", "p3"), groups[0].variants.map { it.id })
    }

    @Test
    fun `products without a group id become solo groups keyed by their own id`() {
        val solo = product(id = "p1", name = "Sugar", pricePaise = 4500)

        val groups = listOf(solo).toVariantGroups()

        assertEquals(1, groups.size)
        assertEquals("solo:p1", groups[0].groupId)
        assertFalse(groups[0].hasMultipleVariants)
        assertEquals("p1", groups[0].defaultVariant.id)
    }

    @Test
    fun `inactive products are excluded from groups`() {
        val active = product(
            id = "p1", name = "Tea", pricePaise = 10000,
            variantGroupId = "g-tea", variantLabel = "100 g"
        )
        val inactive = product(
            id = "p2", name = "Tea", pricePaise = 18000,
            variantGroupId = "g-tea", variantLabel = "200 g", active = false
        )

        val groups = listOf(active, inactive).toVariantGroups()

        assertEquals(1, groups.size)
        assertEquals(listOf("p1"), groups[0].variants.map { it.id })
    }

    @Test
    fun `common base name is the shared prefix when names share a prefix`() {
        val short = product(
            id = "p1", name = "Amul Milk", pricePaise = 2500,
            variantGroupId = "g-milk", variantLabel = "500 ml"
        )
        val long = product(
            id = "p2", name = "Amul Milk Family Pack", pricePaise = 4800,
            variantGroupId = "g-milk", variantLabel = "1 L"
        )

        val groups = listOf(short, long).toVariantGroups()

        assertEquals("Amul Milk", groups[0].baseName)
    }

    @Test
    fun `common base name falls back to the cheapest product when names differ`() {
        val a = product(
            id = "p1", name = "Colgate", pricePaise = 5000,
            variantGroupId = "g-paste", variantLabel = "100 g"
        )
        val b = product(
            id = "p2", name = "Pepsodent", pricePaise = 4000,
            variantGroupId = "g-paste", variantLabel = "200 g"
        )

        val groups = listOf(a, b).toVariantGroups()

        // Names share no prefix, so the cheapest member supplies the name.
        assertEquals("Pepsodent", groups[0].baseName)
    }

    @Test
    fun `groups are sorted by base name case-insensitively`() {
        val beta = product(
            id = "p2", name = "beta", pricePaise = 1000,
            variantGroupId = "g-beta", variantLabel = "1"
        )
        val alpha = product(
            id = "p1", name = "Alpha", pricePaise = 1000,
            variantGroupId = "g-alpha", variantLabel = "1"
        )

        val groups = listOf(beta, alpha).toVariantGroups()

        assertEquals(listOf("Alpha", "beta"), groups.map { it.baseName })
    }

    @Test
    fun `packLabel prefers variantLabel then packSizeLabel then unit label`() {
        val withVariant = product(
            id = "p1", name = "Milk", pricePaise = 2500,
            variantGroupId = "g-milk", variantLabel = "500 ml"
        )
        val withPackSize = product(
            id = "p2", name = "Biscuits", pricePaise = 2000,
            packSizeLabel = "Family Pack"
        )
        val weighted = product(
            id = "p3", name = "Rice", pricePaise = 50000,
            quantityType = QuantityType.WEIGHTED,
            priceBasisQty = 1000, unitLabel = "kg"
        )

        assertEquals("500 ml", withVariant.packLabel)
        assertEquals("Family Pack", withPackSize.packLabel)
        assertEquals("1000 g", weighted.packLabel)
    }

    @Test
    fun `variantsMatch searches the pack label and sku`() {
        val milk = product(
            id = "p1", name = "Amul Toned Milk", pricePaise = 2500,
            variantGroupId = "g-milk", variantLabel = "500 ml", sku = "AMUL-500"
        )

        assertTrue(milk.variantsMatch("500"))
        assertTrue(milk.variantsMatch("amul-500"))
        assertTrue(milk.variantsMatch("  500 ML  "))
        assertFalse(milk.variantsMatch("2 L"))
        assertFalse(milk.variantsMatch(""))
    }

    @Test
    fun `unbuyable-only group still surfaces a default variant`() {
        val out = product(
            id = "p1", name = "Honey", pricePaise = 15000,
            variantGroupId = "g-honey", variantLabel = "500 g", stockQty = 0, available = false
        )

        val groups = listOf(out).toVariantGroups()

        assertEquals(1, groups.size)
        assertEquals("p1", groups[0].defaultVariant.id)
    }
}
