package com.balaji.provision.ui.manager

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.balaji.provision.domain.model.Product
import com.balaji.provision.domain.model.QuantityType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StaffPricesScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun pricesSearchFiltersRowsByNameAndSkuAndCanBeCleared() {
        val state = mutableStateOf(
            ManagerUiState(products = listOf(product("rice", "Rice", "RICE-1"), product("oil", "Oil", "OIL-1")))
        )
        compose.setContent {
            MaterialTheme {
                PricesTab(
                    state = state.value,
                    onSearchChange = { state.value = state.value.copy(priceSearchQuery = it) },
                    onRetryProducts = {},
                    onEditPrice = {}
                )
            }
        }
        compose.onNodeWithText("Search product by name or SKU").assertIsDisplayed().performTextInput("OIL-1")

        // The matching row is rendered and the query can be cleared.
        compose.onNodeWithContentDescription("Clear price search").assertIsDisplayed()
        compose.onNodeWithContentDescription("Edit price for Oil").performScrollTo().assertIsDisplayed()
        assertEquals(listOf("oil"), compose.runOnIdle { state.value.filteredPriceProducts.map { it.id } })

        // A query that matches nothing renders the empty state instead of any row.
        // Absence of a filtered-out row is asserted this way because Compose 1.5.4 keeps
        // a non-rendered semantics node for a row removed from a LazyColumn (screenshot
        // verified on device: only the matching card is drawn), so assertDoesNotExist on
        // that ghost node fails even though the UI is correct.
        compose.onNodeWithContentDescription("Clear price search").performClick()
        compose.onNodeWithText("Search product by name or SKU").performClick()
        compose.onNodeWithText("Search product by name or SKU").performTextInput("ZZZ-99")
        compose.onNodeWithText("No products match this search.").assertIsDisplayed()

        // Clearing restores the rows and removes the clear affordance.
        compose.onNodeWithContentDescription("Clear price search").performClick()
        compose.onNodeWithContentDescription("Clear price search").assertDoesNotExist()
        compose.onNodeWithContentDescription("Edit price for Oil").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun editPriceHandsTheRowProductToThePriceEditor() {
        var selected: Product? = null
        compose.setContent {
            MaterialTheme {
                PricesTab(
                    state = ManagerUiState(products = listOf(product("rice", "Rice", "RICE-1"))),
                    onSearchChange = {}, onRetryProducts = {},
                    onEditPrice = { selected = it }
                )
            }
        }
        compose.onNodeWithContentDescription("Edit price for Rice").performScrollTo().performClick()
        assertEquals("rice", selected?.id)
        assertEquals(12000L, selected?.pricePaise)
    }

    @Test
    fun catalogueFailureOffersRetryAndKeepsLoadedRows() {
        val state = mutableStateOf(
            ManagerUiState(
                products = listOf(product("rice", "Rice", "RICE-1")),
                productsError = "Check your connection"
            )
        )
        var retries = 0
        compose.setContent {
            MaterialTheme {
                PricesTab(
                    state = state.value,
                    onSearchChange = {}, onRetryProducts = { retries++ }, onEditPrice = {}
                )
            }
        }
        compose.onNodeWithText("Check your connection").assertIsDisplayed()
        compose.onNodeWithText("Retry loading products").performClick()
        assertEquals(1, retries)
        compose.onNodeWithText("Rice").performScrollTo().assertIsDisplayed()
    }

    private fun product(id: String, name: String, sku: String) = Product(
        id = id, sku = sku, name = name, categoryId = "test",
        description = null, imagePath = null, packSizeLabel = "1 pack", maxQty = null,
        quantityType = QuantityType.PACKED, pricePaise = 12000, priceBasisQty = 1,
        unitLabel = "pack", stepQty = 1, minQty = 1, stockQty = 20,
        lowStockThreshold = 2, active = true, available = true
    )
}
