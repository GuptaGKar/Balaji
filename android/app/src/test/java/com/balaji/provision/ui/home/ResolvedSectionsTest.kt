package com.balaji.provision.ui.home

import com.balaji.provision.data.repository.SectionKind
import com.balaji.provision.data.repository.StorefrontSection
import com.balaji.provision.domain.model.Category
import com.balaji.provision.domain.model.Product
import com.balaji.provision.domain.model.QuantityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The skip-and-hide rules for composed sections.
 *
 * These are the rules that decide whether the feature is safe to ship. A section that renders a
 * product the server will not sell, or a heading with nothing under it, is a customer-facing
 * lie; both must be impossible. Every test here is about what gets dropped and what survives.
 */
class ResolvedSectionsTest {

    private fun product(
        id: String,
        available: Boolean = true,
        stockQty: Long = 10,
        minQty: Long = 1
    ) = Product(
        id = id,
        sku = "SKU-$id",
        name = "Product $id",
        description = null,
        categoryId = "cat-1",
        imagePath = null,
        quantityType = QuantityType.PACKED,
        pricePaise = 6000,
        priceBasisQty = 1,
        unitLabel = "pack",
        packSizeLabel = "1 pc",
        stepQty = 1,
        minQty = minQty,
        maxQty = null,
        stockQty = stockQty,
        lowStockThreshold = 3,
        active = true,
        available = available
    )

    private fun category(id: String, active: Boolean = true) =
        Category(id = id, name = "Category $id", slug = id.lowercase(), sortOrder = 0, active = active)

    private fun section(
        id: String,
        type: SectionKind,
        itemIds: List<String>,
        title: String = "Section $id"
    ) = StorefrontSection(
        id = id,
        title = title,
        subtitle = null,
        type = type,
        itemIds = itemIds
    )

    @Test
    fun `a products rail resolves in the manager order`() {
        val result = resolveStorefrontSections(
            sections = listOf(section("s1", SectionKind.Products, listOf("p-2", "p-1"))),
            products = listOf(product("p-1"), product("p-2")),
            categories = emptyList()
        )
        val rail = result.single() as ResolvedProductRail
        assertEquals(listOf("p-2", "p-1"), rail.products.map { it.id })
    }

    @Test
    fun `sections themselves keep their order`() {
        val result = resolveStorefrontSections(
            sections = listOf(
                section("s1", SectionKind.Products, listOf("p-1")),
                section("s2", SectionKind.Products, listOf("p-2"))
            ),
            products = listOf(product("p-1"), product("p-2")),
            categories = emptyList()
        )
        assertEquals(listOf("s1", "s2"), result.map { it.id })
    }

    @Test
    fun `a deleted product is skipped and the rest of the rail survives`() {
        val result = resolveStorefrontSections(
            sections = listOf(section("s1", SectionKind.Products, listOf("p-1", "gone", "p-2"))),
            products = listOf(product("p-1"), product("p-2")),
            categories = emptyList()
        )
        val rail = result.single() as ResolvedProductRail
        assertEquals(listOf("p-1", "p-2"), rail.products.map { it.id })
    }

    @Test
    fun `an out-of-stock product is skipped`() {
        val result = resolveStorefrontSections(
            sections = listOf(section("s1", SectionKind.Products, listOf("p-1", "p-2"))),
            products = listOf(product("p-1", stockQty = 0), product("p-2")),
            categories = emptyList()
        )
        val rail = result.single() as ResolvedProductRail
        assertEquals(listOf("p-2"), rail.products.map { it.id })
    }

    @Test
    fun `a product below its minimum quantity counts as out of stock`() {
        // isInStock is stockQty >= minQty, so a weighted item with 50 g on hand and a 100 g
        // minimum cannot be bought and must not be offered.
        val result = resolveStorefrontSections(
            sections = listOf(section("s1", SectionKind.Products, listOf("p-1"))),
            products = listOf(product("p-1", stockQty = 50, minQty = 100)),
            categories = emptyList()
        )
        assertTrue("section with nothing to draw must disappear", result.isEmpty())
    }

    @Test
    fun `an unavailable product is skipped`() {
        val result = resolveStorefrontSections(
            sections = listOf(section("s1", SectionKind.Products, listOf("p-1", "p-2"))),
            products = listOf(product("p-1", available = false), product("p-2")),
            categories = emptyList()
        )
        assertEquals(listOf("p-2"), (result.single() as ResolvedProductRail).products.map { it.id })
    }

    @Test
    fun `a section whose items all fail to resolve disappears rather than rendering empty`() {
        val result = resolveStorefrontSections(
            sections = listOf(section("s1", SectionKind.Products, listOf("gone-1", "gone-2"))),
            products = listOf(product("p-1")),
            categories = emptyList()
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `an inactive category is skipped and a grid of only inactive categories disappears`() {
        val mixed = resolveStorefrontSections(
            sections = listOf(section("s1", SectionKind.Categories, listOf("c-1", "c-2"))),
            products = emptyList(),
            categories = listOf(category("c-1"), category("c-2", active = false))
        )
        assertEquals(listOf("c-1"), (mixed.single() as ResolvedCategoryGrid).categories.map { it.id })

        val allInactive = resolveStorefrontSections(
            sections = listOf(section("s1", SectionKind.Categories, listOf("c-2"))),
            products = emptyList(),
            categories = listOf(category("c-2", active = false))
        )
        assertTrue(allInactive.isEmpty())
    }

    @Test
    fun `heading and subtitle are carried through unchanged`() {
        val withSubtitle = listOf(
            StorefrontSection("s1", "Daily essentials", "Low prices", SectionKind.Products, listOf("p-1"))
        )
        val result = resolveStorefrontSections(withSubtitle, listOf(product("p-1")), emptyList())
        assertEquals("Daily essentials", result.single().title)
        assertEquals("Low prices", result.single().subtitle)
    }

    @Test
    fun `no sections at all yields nothing, which is the fallback signal`() {
        assertTrue(resolveStorefrontSections(emptyList(), listOf(product("p-1")), emptyList()).isEmpty())
    }

    // ---- ungrouped tail for the Categories page ----

    @Test
    fun `categories no section placed are reported so none become unreachable`() {
        val resolved = resolveStorefrontSections(
            sections = listOf(section("s1", SectionKind.Categories, listOf("c-1"))),
            products = emptyList(),
            categories = listOf(category("c-1"), category("c-2"), category("c-3"))
        )
        assertEquals(listOf("c-2", "c-3"), ungroupedCategoryIds(resolved, listOf(category("c-1"), category("c-2"), category("c-3"))))
    }

    @Test
    fun `when every category is placed the tail is empty and must not render`() {
        val all = listOf(category("c-1"), category("c-2"))
        val resolved = resolveStorefrontSections(
            sections = listOf(section("s1", SectionKind.Categories, listOf("c-1", "c-2"))),
            products = emptyList(),
            categories = all
        )
        assertTrue(ungroupedCategoryIds(resolved, all).isEmpty())
    }

    @Test
    fun `a products section does not count as placing a category`() {
        val all = listOf(category("c-1"), category("c-2"))
        val resolved = resolveStorefrontSections(
            sections = listOf(section("s1", SectionKind.Products, listOf("p-1"))),
            products = listOf(product("p-1")),
            categories = all
        )
        assertEquals(listOf("c-1", "c-2"), ungroupedCategoryIds(resolved, all))
    }
}
