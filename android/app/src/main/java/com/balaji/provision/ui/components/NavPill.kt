package com.balaji.provision.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

/**
 * The customer floating navigation pill, and the single source of truth for how much room it
 * needs.
 *
 * The pill is an overlay: it draws over the page rather than reserving a strip at the bottom of
 * the window. That is the whole reason the reserve constants live here next to the composable -
 * every screen under the tab scaffold has to add the reserve to its own scroll padding, and if
 * that number can drift from the pill's real height it will. When it does, the last row of a
 * list is permanently unreachable, which is worse than the strip the pill replaced.
 */

/** Height of the pill's own row. */
const val NAV_PILL_HEIGHT_DP = 64

/**
 * Vertical padding inside the pill, i.e. how much of [NAV_PILL_HEIGHT_DP] a tab does NOT get.
 * Sixty-four minus twice this still leaves a 48 dp target, which is the accessibility floor.
 */
const val NAV_PILL_INNER_VERTICAL_PADDING_DP = 8

/** Gap the pill keeps from the bottom of the window (above the system bar inset). */
const val NAV_PILL_BOTTOM_MARGIN_DP = 8

/**
 * Extra room for the shadow the pill casts and for the outline, so content does not visually
 * collide with the pill's own elevation.
 */
const val NAV_PILL_ELEVATION_ALLOWANCE_DP = 12

/** How much bottom padding a scrollable screen needs to clear the pill. */
const val NAV_PILL_RESERVE_DP =
    NAV_PILL_HEIGHT_DP + NAV_PILL_BOTTOM_MARGIN_DP + NAV_PILL_ELEVATION_ALLOWANCE_DP

/**
 * Height of [MiniCartBar] including its own vertical padding. A screen that shows the basket bar
 * needs it stacked on top of the pill, so it reserves this much again.
 */
const val MINI_CART_BAR_RESERVE_DP = 72

/**
 * Bottom padding that clears the pill, plus the basket bar when the screen is showing one.
 *
 * Screens take this as `contentPadding.bottom` so the final row can be scrolled into a region
 * nothing is drawn over.
 */
fun navPillReserve(basketBarVisible: Boolean = false): Dp =
    (NAV_PILL_RESERVE_DP + if (basketBarVisible) MINI_CART_BAR_RESERVE_DP else 0).dp

data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

/**
 * Opacity of the pill's background. The owner asked for "70% transparency", which is 30% opaque,
 * and confirmed that reading on 2026-11-05. If they meant 70% opaque, this single constant
 * becomes 0.70f and nothing else changes.
 */
private const val NAV_PILL_ALPHA = 0.30f

/**
 * Tabs the customer shell exposes, in the order they are drawn. [NavPill] and the shell's
 * navigation share this one list, so a tab cannot appear in the bar without a route to go to.
 */
fun customerNavItems(): List<NavItem> = listOf(
    NavItem("home", "Home", Icons.Outlined.Home),
    NavItem("categories", "Categories", Icons.Filled.GridView),
    NavItem("orders", "Orders", Icons.Outlined.ReceiptLong),
    NavItem("cart", "Basket", Icons.Outlined.ShoppingBag)
)

/**
 * A fully rounded, translucent, centred pill floating over the page content.
 *
 * Because the background is see-through, the 1 dp outline and the shadow are not decoration:
 * without them the labels dissolve into a busy product image scrolling behind the pill. The
 * width is capped so the pill stays a phone-shaped control on a tablet instead of a full-width
 * strip. Each target is at least 48 dp tall.
 */
@Composable
fun NavPill(
    navController: NavHostController,
    cartCount: Int,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val destinations = customerNavItems()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route.orEmpty()
    val selectedColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = NAV_PILL_ALPHA),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 8.dp,
        modifier = modifier
            .padding(horizontal = 16.dp)
            .widthIn(max = 480.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(NAV_PILL_HEIGHT_DP.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            destinations.forEach { item ->
                // The product listing is the category browser's destination, so keep
                // Categories highlighted while a listing is open.
                val selected = currentRoute == item.route ||
                    (item.route == "categories" && currentRoute.startsWith("listing/"))
                val tint = if (selected) selectedColor else inactiveColor
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 2.dp, vertical = NAV_PILL_INNER_VERTICAL_PADDING_DP.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            if (selected) selectedColor.copy(alpha = 0.14f) else Color.Transparent
                        )
                        .selectable(
                            selected = selected,
                            onClick = { onSelect(item.route) },
                            role = Role.Tab
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (item.route == "cart" && cartCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge(containerColor = selectedColor) {
                                    Text(
                                        text = if (cartCount > 99) "99+" else cartCount.toString(),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        ) {
                            Icon(
                                item.icon,
                                // Decorative: the visible label under the icon already names the
                                // tab, and a contentDescription here would be read twice.
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    } else {
                        Icon(
                            item.icon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.label,
                        color = tint,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
