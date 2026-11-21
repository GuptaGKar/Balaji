package com.balaji.provision.ui.catalog

import com.balaji.provision.domain.model.Product
import com.balaji.provision.domain.model.QuantityType
import com.balaji.provision.domain.model.toVariantGroups
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The catalogue grid lists **one row per pack**.
 *
 * Before this, the grid rendered one card per `ProductVariantGroup` and relied on a summary
 * line to say the group had other sizes, so a customer browsing the list never saw the 650 g
 * pack, its price or its own ADD control. These cases pin the behaviour the owner asked for:
 * every pack is a row of its own, the in-stock filter is per pack, and each sort orders the
 * rows the customer actually sees.
 */
class CatalogListingTest {

    private fun product(
        id: String,
        name: String,
        pricePaise: Long,
        variantGroupId: String? = null,
        variantLabel: String? = null,
        stockQty: Long = 10,
        minQty: Long = 1,
        quantityType: QuantityType = QuantityType.PACKED,
        unitLabel: String = "pack",
        priceBasisQty: Long = 1
    ) = Product(
        id = id,
        sku = "SKU-$id",
        name = name,
        description = null,
        categoryId = "cat-1",
        imagePath = null,
        quantityType = quantityType,
        pricePaise = pricePaise,
        priceBasisQty = priceBasisQty,
        unitLabel = unitLabel,
        packSizeLabel = null,
        stepQty = 1,
        minQty = minQty,
        maxQty = null,
        stockQty = stockQty,
        lowStockThreshold = 3,
        active = true,
        available = true,
        variantGroupId = variantGroupId,
        variantLabel = variantLabel
    )

    private val sauceGroup = listOf(
        product(
            id = "p1", name = "9AM Green Chilli Sauce", pricePaise = 4800,
            variantGroupId = "g-sauce", variantLabel = "180 g"
        ),
        product(
            id = "p2", name = "9AM Green Chilli Sauce", pricePaise = 12000,
            variantGroupId = "g-sauce", variantLabel = "650 g"
        )
    )

    @Test
    fun `every pack of a grouped product gets its own row`() {
        val groups = (sauceGroup + product(id = "p3", name = "Tops Soya Sauce", pricePaise = 6300))
            .toVariantGroups()

        val rows = groups.listingProducts(sortOption = SortOption.DEFAULT, inStockOnly = false)

        assertEquals(listOf("p1", "p2", "p3"), rows.map { it.id })
        // Each row carries its own price: the 650 g pack is not priced as the 180 g pack.
        assertEquals(listOf(4800L, 12000L, 6300L), rows.map { it.pricePaise })
    }

    @Test
    fun `a grouped product with one pack in stock still lists that pack`() {
        val groups = listOf(
            product(
                id = "p1", name = "Amul Toned Milk", pricePaise = 2500,
                variantGroupId = "g-milk", variantLabel = "500 ml", stockQty = 10
            ),
            product(
                id = "p2", name = "Amul Toned Milk", pricePaise = 4800,
                variantGroupId = "g-milk", variantLabel = "1 L", stockQty = 0
            )
        ).toVariantGroups()

        val inStockOnly = groups.listingProducts(sortOption = SortOption.DEFAULT, inStockOnly = true)
        val all = groups.listingProducts(sortOption = SortOption.DEFAULT, inStockOnly = false)

        assertEquals(listOf("p1"), inStockOnly.map { it.id })
        assertEquals(listOf("p1", "p2"), all.map { it.id })
    }

    @Test
    fun `price sort orders the rows themselves, not one price per group`() {
        val groups = (sauceGroup + product(id = "p3", name = "Tops Soya Sauce", pricePaise = 6300))
            .toVariantGroups()

        val ascending =
            groups.listingProducts(sortOption = SortOption.PRICE_LOW, inStockOnly = false)
        val descending =
            groups.listingProducts(sortOption = SortOption.PRICE_HIGH, inStockOnly = false)

        // 48.00 (group) < 63.00 (solo) < 120.00 (group): the expensive pack of the grouped
        // product must sort on its own price, not behind the group's cheapest pack.
        assertEquals(listOf("p1", "p3", "p2"), ascending.map { it.id })
        assertEquals(listOf("p2", "p3", "p1"), descending.map { it.id })
    }

    @Test
    fun `name sort orders by the product name shown on the card`() {
        val groups = listOf(
            product(
                id = "p2", name = "Zeta Sauce", pricePaise = 5000,
                variantGroupId = "g-z", variantLabel = "200 g"
            ),
            product(
                id = "p1", name = "Alpha Sauce", pricePaise = 4000,
                variantGroupId = "g-a", variantLabel = "100 g"
            )
        ).toVariantGroups()

        val rows = groups.listingProducts(sortOption = SortOption.NAME, inStockOnly = false)

        assertEquals(listOf("Alpha Sauce", "Zeta Sauce"), rows.map { it.name })
    }
}
