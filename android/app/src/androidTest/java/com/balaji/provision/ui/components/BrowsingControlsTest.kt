package com.balaji.provision.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.balaji.provision.domain.model.CartItem
import com.balaji.provision.domain.model.Product
import com.balaji.provision.domain.model.QuantityType
import com.balaji.provision.ui.theme.BalajiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BrowsingControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun clearSearchHasItsOwnTouchTargetOutsideTheEditableText() {
        val query = mutableStateOf("Whole wheat flour")
        compose.setContent {
            BalajiTheme {
                Box(Modifier.width(320.dp)) {
                    StoreSearchBar(query = query.value, onQueryChange = { query.value = it })
                }
            }
        }
        val clear = compose.onNodeWithContentDescription("Clear search")
        clear.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        val textBounds = compose.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
        val clearBounds = clear.fetchSemanticsNode().boundsInRoot
        assertTrue("Clear must not cover typed text", textBounds.right <= clearBounds.left)
        clear.performClick()
        compose.onNode(hasSetTextAction()).assertTextEquals("")
    }

    @Test
    fun imeSearchReleasesFocusSoResultsAreVisible() {
        compose.setContent {
            BalajiTheme { StoreSearchBar(query = "rice", onQueryChange = {}) }
        }
        val input = compose.onNode(hasSetTextAction())
        input.performClick().performImeAction()
        input.assertIsNotFocused()
    }

    @Test
    fun addingAProductKeepsThePriceVisibleAndPutsTheControlBelowIt() {
        val quantity = mutableStateOf(0L)
        val product = product()
        compose.setContent {
            BalajiTheme {
                Box(Modifier.width(164.dp)) {
                    ProductGridCard(
                        product = product,
                        cartItem = quantity.value.takeIf { it > 0 }?.let { CartItem(product.id, it) },
                        onOpenDetails = {}, onAdd = { quantity.value = 1 },
                        onIncrease = { quantity.value++ }, onDecrease = { quantity.value-- },
                        modifier = Modifier.testTag("product-card")
                    )
                }
            }
        }
        val add = compose.onNodeWithText("ADD").fetchSemanticsNode().boundsInRoot
        val priceBefore = compose.onNodeWithText("₹199.00").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "ADD shares the price row while the line is not in the basket",
            add.top < priceBefore.bottom && add.left > priceBefore.left
        )

        compose.onNodeWithText("ADD").performClick()
        compose.waitForIdle()

        // The bug this replaced: the stepper measured ~124 dp on a 144 dp content line, the
        // weighted price column measured zero, and the amount disappeared the moment the
        // customer tapped ADD. The price must survive, and the control must sit under it.
        val price = compose.onNodeWithText("₹199.00").fetchSemanticsNode().boundsInRoot
        assertTrue("Price must still have a width after adding", price.width > 1f)
        val control = compose
            .onNodeWithContentDescription("Test rice")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(
            "The stepper must sit below the price, not on top of it",
            control.top >= price.bottom - 1f
        )
        // Containment is against the card, not against the price text: the text stops where its
        // glyphs stop, while the stepper is end-aligned to the card's content edge, so the text
        // is the wrong yardstick (it made this assertion fail on a stepper inside the card).
        val card = compose.onNodeWithTag("product-card").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "The stepper must stay inside the card",
            control.right <= card.right + 1f && control.left >= card.left - 1f
        )
    }

    @Test
    fun unavailableItemCanBeRemovedButCannotBeIncreased() {
        var increases = 0
        var decreases = 0
        compose.setContent {
            BalajiTheme {
                AnimatedAddQuantityControl(
                    quantityBase = 1, onAdd = {}, onIncrement = { increases++ },
                    onDecrement = { decreases++ }, productName = "Test rice",
                    enabled = false, canIncrease = true
                )
            }
        }
        compose.onNodeWithContentDescription("Add one more to Test rice").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Remove one from Test rice").performClick()
        assertEquals(0, increases)
        assertEquals(1, decreases)
    }

    private fun product() = Product(
        id = "synthetic-rice", sku = "UI-TEST", name = "Test rice", categoryId = "test",
        description = null, imagePath = null, packSizeLabel = "1 kg", maxQty = null,
        quantityType = QuantityType.PACKED, pricePaise = 19900, mrpPaise = 24900,
        priceBasisQty = 1, unitLabel = "pack", stepQty = 1, minQty = 1,
        stockQty = 10, lowStockThreshold = 2, active = true, available = true
    )
}
