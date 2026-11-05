package com.balaji.provision.data.repository

import com.balaji.provision.data.network.StorefrontSectionResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decoding and cache rules for composed storefront content.
 *
 * The two properties worth pinning are the ones that decide whether the feature is safe to
 * ship: an unrecognised section type must disappear rather than render as something else, and
 * an empty result must mean "use the built-in layout" rather than "show a blank page".
 */
class StorefrontRepositoryTest {

    private fun row(
        id: String? = "s-1",
        title: String? = "Daily essentials",
        subtitle: String? = null,
        type: String? = "products",
        itemIds: List<String>? = listOf("p-1", "p-2")
    ) = StorefrontSectionResponse(
        id = id,
        title = title,
        subtitle = subtitle,
        section_type = type,
        updated_at = "2026-11-05T00:00:00Z",
        item_ids = itemIds
    )

    // ---- decoding ----

    @Test
    fun `a products section decodes with its ids in server order`() {
        val section = row().toDomain()
        assertEquals("s-1", section?.id)
        assertEquals(SectionKind.Products, section?.type)
        assertEquals(listOf("p-1", "p-2"), section?.itemIds)
    }

    @Test
    fun `a categories section decodes as categories`() {
        val section = row(type = "categories").toDomain()
        assertEquals(SectionKind.Categories, section?.type)
    }

    @Test
    fun `an unknown section type is dropped, not guessed at`() {
        // A future server could add a type this build has never seen. Rendering it as a product
        // rail would be wrong in a way nobody notices; dropping it leaves the rest of the page
        // intact, which is the failure mode we want.
        assertNull(row(type = "countdown_timer").toDomain())
    }

    @Test
    fun `a missing type is dropped too`() {
        assertNull(row(type = null).toDomain())
    }

    @Test
    fun `a row with no id is dropped because it cannot be keyed`() {
        assertNull(row(id = null).toDomain())
    }

    @Test
    fun `section type is matched case-insensitively`() {
        assertEquals(SectionKind.Products, row(type = "PRODUCTS").toDomain()?.type)
    }

    @Test
    fun `blank item ids are filtered out rather than rendered as empty cards`() {
        val section = row(itemIds = listOf("p-1", "", "p-2")).toDomain()
        assertEquals(listOf("p-1", "p-2"), section?.itemIds)
    }

    @Test
    fun `a null item list becomes an empty one, not a crash`() {
        assertEquals(emptyList<String>(), row(itemIds = null).toDomain()?.itemIds)
    }

    @Test
    fun `a blank subtitle is treated as absent so no empty line renders`() {
        assertEquals(null, row(subtitle = "   ").toDomain()?.subtitle)
        assertEquals("Real subtitle", row(subtitle = "Real subtitle").toDomain()?.subtitle)
    }

    // ---- cache freshness ----

    private val t0 = 1_000_000L

    @Test
    fun `a cache for the same page inside the window is served`() {
        assertTrue(StorefrontRepository.isFresh("home", "home", t0, t0 + 60_000, force = false))
    }

    @Test
    fun `a cache for the other page is never served`() {
        assertFalse(StorefrontRepository.isFresh("home", "categories", t0, t0, force = false))
    }

    @Test
    fun `a cache older than the window is refetched`() {
        assertFalse(
            StorefrontRepository.isFresh(
                "home", "home", t0, t0 + StorefrontRepository.REFRESH_MILLIS, force = false
            )
        )
        assertTrue(
            StorefrontRepository.isFresh(
                "home", "home", t0, t0 + StorefrontRepository.REFRESH_MILLIS - 1, force = false
            )
        )
    }

    @Test
    fun `a forced read ignores freshness, which is how a re-entered page refreshes`() {
        assertFalse(StorefrontRepository.isFresh("home", "home", t0, t0, force = true))
    }

    @Test
    fun `no cached page at all is never fresh`() {
        assertFalse(StorefrontRepository.isFresh(null, "home", t0, t0, force = false))
    }

    @Test
    fun `the refresh window is five minutes, matching the brief`() {
        assertEquals(5 * 60 * 1000L, StorefrontRepository.REFRESH_MILLIS)
    }
}
