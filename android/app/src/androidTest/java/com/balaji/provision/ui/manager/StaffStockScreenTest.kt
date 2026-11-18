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
import com.balaji.provision.domain.model.LowStockProduct
import com.balaji.provision.domain.model.Product
import com.balaji.provision.domain.model.QuantityType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StaffStockScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun normalStockProductCanOpenStockEditor() {
        var selected: LowStockProduct? = null
        compose.setContent {
            MaterialTheme {
                StockTab(
                    state = ManagerUiState(products = listOf(product())),
                    onSearchChange = {}, onRetryProducts = {},
                    onAdjust = { selected = it }
                )
            }
        }
        compose.onNodeWithContentDescription("Edit stock for Rice").performScrollTo().performClick()
        assertEquals("rice", selected?.id)
        assertEquals(20L, selected?.stockQty)
    }

    @Test
    fun searchAppearsBeforeLowStockAndFiltersBothLists() {
        val rice = product()
        val oil = rice.copy(id = "oil", sku = "OIL-1", name = "Oil", stockQty = 0)
        val state = mutableStateOf(ManagerUiState(
            products = listOf(rice, oil), lowStock = listOf(oil.toStockAdjustmentProduct())
        ))
        compose.setContent {
            MaterialTheme {
                StockTab(
                    state = state.value,
                    onSearchChange = { state.value = state.value.copy(stockSearchQuery = it) },
                    onRetryProducts = {}, onAdjust = {}
                )
            }
        }
        compose.onNodeWithText("Search product by name or SKU").assertIsDisplayed().performTextInput("RICE-1")
        compose.onNodeWithText("Low stock alert").assertDoesNotExist()
        compose.onNodeWithContentDescription("Edit stock for Rice").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Clear stock search").performClick()
        compose.onNodeWithText("Low stock alert").assertIsDisplayed()
    }

    @Test
    fun stockPageOffersStockEditingOnly() {
        compose.setContent {
            MaterialTheme {
                StockTab(
                    state = ManagerUiState(products = listOf(product())),
                    onSearchChange = {}, onRetryProducts = {}, onAdjust = {}
                )
            }
        }
        compose.onNodeWithContentDescription("Edit stock for Rice").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Edit Price").assertDoesNotExist()
    }

    @Test
    fun stockEditorHasNoPriceMode() {
        compose.setContent {
            MaterialTheme {
                AdjustStockDialog(
                    product = product().toStockAdjustmentProduct(),
                    stockBusy = false, error = null,
                    onDismiss = {}, onConfirmStock = { _, _ -> }
                )
            }
        }
        compose.onNodeWithText("Adjust Price & MRP").assertDoesNotExist()
        compose.onNodeWithText("Save Price").assertDoesNotExist()
        compose.onNodeWithText("Save Stock").assertIsDisplayed()
    }

    @Test
    fun failedSaveShowsErrorInsideEditorAndRetainsEnteredQuantity() {
        val error = mutableStateOf<String?>(null)
        compose.setContent {
            MaterialTheme {
                AdjustStockDialog(
                    product = product().toStockAdjustmentProduct(),
                    stockBusy = false, error = error.value,
                    onDismiss = {}, onConfirmStock = { _, _ -> }
                )
            }
        }
        compose.onNodeWithText("Quantity in packs").performTextInput("3")
        compose.runOnIdle { error.value = "Could not reach the store" }
        compose.onNodeWithText("Could not reach the store").assertIsDisplayed()
        compose.onNodeWithText("3").assertIsDisplayed()
    }

    private fun product() = Product(
        id = "rice", sku = "RICE-1", name = "Rice", categoryId = "test",
        description = null, imagePath = null, packSizeLabel = "1 pack", maxQty = null,
        quantityType = QuantityType.PACKED, pricePaise = 12000, priceBasisQty = 1,
        unitLabel = "pack", stepQty = 1, minQty = 1, stockQty = 20,
        lowStockThreshold = 2, active = true, available = true
    )
}
