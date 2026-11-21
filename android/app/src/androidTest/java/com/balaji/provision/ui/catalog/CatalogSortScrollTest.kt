package com.balaji.provision.ui.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

/**
 * Sorting reorders the catalogue grid underneath the scroll offset the customer
 * has already scrolled to, so the item that is now first can be left
 * off-screen. These tests pin the two halves of the contract: a sort change
 * returns the list to the top, and anything else leaves the position alone.
 */
class CatalogSortScrollTest {
    @get:Rule val compose = createComposeRule()

    private val rows = (0 until 120).map { index -> "Row $index" }

    @Test
    fun changingTheSortReturnsTheGridToTheTop() {
        var sortOption by mutableStateOf(SortOption.DEFAULT)

        compose.setContent {
            val gridState = rememberCatalogueGridState(sortOption = sortOption)
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                modifier = Modifier.testTag("catalogue-grid")
            ) {
                items(rows) { row ->
                    Text(text = row, modifier = Modifier.height(160.dp))
                }
            }
        }

        compose.onNodeWithTag("catalogue-grid").performScrollToIndex(80)
        compose.waitForIdle()
        // Prove the scroll actually moved, so the assertion after the sort means
        // something rather than passing because the list was never scrolled.
        compose.onNodeWithText("Row 0").assertIsNotDisplayed()

        sortOption = SortOption.PRICE_HIGH
        compose.waitForIdle()

        compose.onNodeWithText("Row 0").assertIsDisplayed()
    }

    @Test
    fun aRecompositionThatIsNotASortChangeKeepsTheScrollPosition() {
        var sortOption by mutableStateOf(SortOption.DEFAULT)
        var cartCount by mutableStateOf(0)

        compose.setContent {
            val gridState = rememberCatalogueGridState(sortOption = sortOption)
            Column {
                // Reading unrelated state lets this test force a recomposition.
                Text(text = "cart=$cartCount", modifier = Modifier.testTag("cart"))
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.testTag("catalogue-grid")
                ) {
                    items(rows) { row ->
                        Text(text = row, modifier = Modifier.height(160.dp))
                    }
                }
            }
        }

        compose.onNodeWithTag("catalogue-grid").performScrollToIndex(80)
        compose.waitForIdle()
        compose.onNodeWithText("Row 0").assertIsNotDisplayed()

        cartCount = 1
        compose.waitForIdle()

        compose.onNodeWithText("Row 0").assertIsNotDisplayed()
    }
}
