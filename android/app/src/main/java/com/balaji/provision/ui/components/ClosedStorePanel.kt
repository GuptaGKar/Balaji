package com.balaji.provision.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.balaji.provision.ui.theme.BalajiDesign
import com.balaji.provision.ui.theme.BalajiText
import com.balaji.provision.ui.theme.appColors

/**
 * The "store is closed" surfaces.
 *
 * The closed-mode mockup promises "Place your order now - we will deliver it tomorrow, 7:00 AM -
 * 11:00 AM". This build cannot say that. `place_order` rejects an order while `store_open` is
 * false, there is no scheduled-delivery column, no slot capacity and no manager toggle for it, so
 * every word here describes what actually happens: browsing and the basket keep working, checkout
 * does not. The day part ("tomorrow") and the window are both future facts this app does not have,
 * and a grocery customer who is told 7-11 AM and gets nothing at 7-11 AM blames the store, not the
 * app.
 *
 * If scheduling is ever built, [closedCopyFor] is the place the sentence changes - and it should
 * only change once the server can name a real window.
 */

/** Where the panel is being shown, because the honest sentence differs by screen. */
enum class ClosedPanelPlace { Home, ProductDetails, Cart }

/**
 * True only when the server can accept an order while the store is closed. Nothing in this build
 * sets it: there is no scheduling column, no slot RPC and no manager switch, and the brief's own
 * rule is that closed-store ordering stays disabled until the owner approves a real configuration.
 */
const val ScheduledOrderingEnabled: Boolean = false

data class ClosedCopy(val title: String, val body: String, val footnote: String?)

/**
 * The copy for a closed store. Pure, so the promise it does or does not make can be asserted in a
 * test instead of being re-read from a screenshot every release.
 */
fun closedCopyFor(place: ClosedPanelPlace, schedulingEnabled: Boolean): ClosedCopy = when {
    schedulingEnabled -> ClosedCopy(
        title = "Store is Closed",
        // Still deliberately vague about *when*: even with scheduling on, the window is per-order
        // server data and belongs next to the chosen slot, not in a panel on Home.
        body = "You can order now for a delivery window the store confirms at checkout.",
        footnote = "The delivery date is confirmed by the store, not by this app."
    )

    place == ClosedPanelPlace.Cart -> ClosedCopy(
        title = "Store is Closed",
        body = "Your basket is kept. Checkout opens again when the store reopens.",
        footnote = null
    )

    place == ClosedPanelPlace.ProductDetails -> ClosedCopy(
        title = "Store is Closed",
        body = "You can still add this to your basket. Checkout opens when the store reopens.",
        footnote = null
    )

    else -> ClosedCopy(
        title = "Store is Closed",
        body = "You can browse the store and keep your basket. Orders are taken when it reopens.",
        footnote = null
    )
}

/**
 * A peach glass panel with the store's real status. Renders nothing while the store is open, so a
 * caller can place it unconditionally in the layout.
 */
@Composable
fun ClosedStorePanel(
    storeOpen: Boolean,
    modifier: Modifier = Modifier,
    place: ClosedPanelPlace = ClosedPanelPlace.Home,
    schedulingEnabled: Boolean = ScheduledOrderingEnabled
) {
    if (storeOpen) return
    val copy = closedCopyFor(place, schedulingEnabled)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(BalajiDesign.RadiusHero),
        color = BalajiDesign.PeachBackground,
        border = BorderStroke(
            BalajiDesign.CardBorder,
            MaterialTheme.colorScheme.error.copy(alpha = 0.28f)
        ),
        shadowElevation = BalajiDesign.CardElevation
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Icon(
                    Icons.Default.Store,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(20.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = copy.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = copy.body,
                    style = BalajiText.Body,
                    color = MaterialTheme.colorScheme.onSurface
                )
                copy.footnote?.let { footnote ->
                    Text(
                        text = footnote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * The small status chip for a header: green while open, red while closed.
 *
 * Kept separate from [ClosedStorePanel] because the header has a few dp, not a paragraph.
 */
@Composable
fun StoreStatusChip(
    storeOpen: Boolean,
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(BalajiDesign.RadiusPill),
        color = if (storeOpen) {
            MaterialTheme.appColors.successContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        },
        border = BorderStroke(1.dp, Color.Transparent)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                Icons.Default.Store,
                contentDescription = null,
                tint = if (storeOpen) {
                    MaterialTheme.appColors.onSuccessContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                },
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (storeOpen) {
                    MaterialTheme.appColors.onSuccessContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                }
            )
        }
    }
}
