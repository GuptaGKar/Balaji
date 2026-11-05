package com.balaji.provision.ui.home

import com.balaji.provision.data.repository.SectionKind
import com.balaji.provision.data.repository.StorefrontSection
import com.balaji.provision.domain.model.Category
import com.balaji.provision.domain.model.Product

/**
 * A composed section with its ids already turned into real catalogue objects.
 *
 * Resolution happens here, in pure code, rather than inside the composable. That keeps the
 * skip-and-hide rules testable without Compose, and keeps a single place where the question
 * "may we show this?" is answered.
 */
sealed interface ResolvedSection {
    val id: String
    val title: String
    val subtitle: String?
}

data class ResolvedProductRail(
    override val id: String,
    override val title: String,
    override val subtitle: String?,
    val products: List<Product>
) : ResolvedSection

data class ResolvedCategoryGrid(
    override val id: String,
    override val title: String,
    override val subtitle: String?,
    val categories: List<Category>
) : ResolvedSection

/**
 * Turns manager-composed ids into what the page can actually draw.
 *
 * Three rules carry the risk, and all three exist so a section can never show a lie:
 *
 *  1. An id that no longer resolves - deleted, or absent from the catalogue this build loaded -
 *     is dropped. The manager's order is kept for everything that survives.
 *  2. A product that is inactive or out of stock is dropped. Showing a card the customer cannot
 *     buy, with a price the server will refuse to charge, is worse than not showing it.
 *  3. A section left with nothing to draw disappears entirely. An empty heading with no shelf
 *     under it is not a layout, and a spinner that never resolves is worse than that.
 *
 * Prices and stock are read from the catalogue objects, never from the section payload, which
 * carries ids only. So a cached section cannot present a stale amount.
 */
fun resolveStorefrontSections(
    sections: List<StorefrontSection>,
    products: List<Product>,
    categories: List<Category>
): List<ResolvedSection> {
    val productsById = products.associateBy { it.id }
    val categoriesById = categories.associateBy { it.id }

    return sections.mapNotNull { section ->
        when (section.type) {
            SectionKind.Products -> {
                val resolved = section.itemIds
                    .mapNotNull { productsById[it] }
                    .filter { it.available && it.isInStock }
                if (resolved.isEmpty()) null
                else ResolvedProductRail(
                    id = section.id,
                    title = section.title,
                    subtitle = section.subtitle,
                    products = resolved
                )
            }

            SectionKind.Categories -> {
                val resolved = section.itemIds
                    .mapNotNull { categoriesById[it] }
                    .filter { it.active }
                if (resolved.isEmpty()) null
                else ResolvedCategoryGrid(
                    id = section.id,
                    title = section.title,
                    subtitle = section.subtitle,
                    categories = resolved
                )
            }
        }
    }
}

/**
 * Ids of the categories no active section places.
 *
 * The Categories page appends these under one final heading so that composing sections can
 * never strand a category: a manager who lists six of forty categories must not make the other
 * thirty-four unreachable, because there would be no way to browse to them at all.
 */
fun ungroupedCategoryIds(
    sections: List<ResolvedSection>,
    allCategories: List<Category>
): List<String> {
    val placed = sections
        .filterIsInstance<ResolvedCategoryGrid>()
        .flatMap { it.categories.map { category -> category.id } }
        .toSet()
    return allCategories.map { it.id }.filterNot { placed.contains(it) }
}
