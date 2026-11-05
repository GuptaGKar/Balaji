package com.balaji.provision.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.balaji.provision.ui.theme.BalajiDesign
import kotlin.math.roundToInt

/**
 * The customer floating navigation pill.
 *
 * It is an overlay rather than a reserved strip, which is what makes it feel like part of the
 * page instead of a chrome bar - and what makes it dangerous. Two things follow from that and
 * are enforced here: the surface has to be opaque enough to read over a photograph, and every
 * screen underneath it has to add [navPillReserve] to its scroll padding or its last row becomes
 * unreachable.
 *
 * The first version shipped at 0.30 alpha because that was the instruction. On a Galaxy A34 in
 * light theme the product names behind it read straight through the tab labels - measured,
 * screenshotted, and logged as BUG-012. The brief now asks for 0.90-0.96, so this uses
 * [BalajiDesign.GlassAlpha] and the pill is legible over a busy rail.
 */

/** Height of the pill's own row. */
const val NAV_PILL_HEIGHT_DP = 64

/** Gap the pill keeps from the bottom of the window (above the system navigation inset). */
const val NAV_PILL_BOTTOM_MARGIN_DP = 8

/** Vertical padding inside the pill: 64 - 2x8 = 48 dp, the accessibility floor. */
const val NAV_PILL_INNER_VERTICAL_PADDING_DP = 8

/**
 * Extra room for the shadow the pill casts and for its outline, so content does not visually
 * collide with the pill's elevation.
 */
const val NAV_PILL_ELEVATION_ALLOWANCE_DP = 12

/** How much bottom padding a scrollable screen needs to clear the pill. */
const val NAV_PILL_RESERVE_DP =
    NAV_PILL_HEIGHT_DP + NAV_PILL_BOTTOM_MARGIN_DP + NAV_PILL_ELEVATION_ALLOWANCE_DP

/**
 * Height of [MiniCartBar] including its own vertical padding. A screen showing the basket bar
 * reserves this again on top of the pill, because the bar stacks above it.
 */
const val MINI_CART_BAR_RESERVE_DP = 72

/** Bottom padding that clears the pill, plus the basket bar when one is visible. */
fun navPillReserve(basketBarVisible: Boolean = false): Dp =
    (NAV_PILL_RESERVE_DP + if (basketBarVisible) MINI_CART_BAR_RESERVE_DP else 0).dp

data class NavItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

/**
 * Tabs the customer shell exposes, in draw order. The pill and the shell navigate from this one
 * list, so a tab cannot appear in the bar without a route that works.
 *
 * The reference board shows five tabs including Offers and Account. Those are not routes in this
 * app, and a pill item that goes nowhere is worse than a missing item, so the four real
 * destinations stay.
 */
fun customerNavItems(): List<NavItem> = listOf(
    NavItem("home", "Home", Icons.Outlined.Home),
    NavItem("categories", "Categories", Icons.Filled.GridView),
    NavItem("orders", "Orders", Icons.Outlined.ReceiptLong),
    NavItem("cart", "Basket", Icons.Outlined.ShoppingBag)
)

/**
 * Whether the device is asking for less animation.
 *
 * Read from the system animator scale rather than a setting of our own: the user has already
 * said this in platform settings, and honouring it is the accessibility requirement. Motion here
 * is decorative - the control works identically at zero.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }
}

/**
 * Which destination is selected, as an index into [customerNavItems].
 *
 * Pulled out as a pure function because this is the rule that decides whether the sliding
 * capsule lands on the right tab, and the listing route is not the Categories route - a
 * `listing/{categoryId}` destination still belongs to Categories.
 */
fun selectedNavIndex(currentRoute: String, destinations: List<NavItem>): Int =
    destinations.indexOfFirst { item ->
        currentRoute == item.route ||
            (item.route == "categories" && currentRoute.startsWith("listing/"))
    }

/**
 * Horizontal offset, in pixels, of the active capsule's left edge for a given destination.
 *
 * Also pure: the capsule must not animate to a position the item is not at, and the arithmetic
 * (equal-weight slots, capsule inset by 4 dp) is exactly the kind of thing that drifts when
 * someone later changes the padding.
 */
fun activeCapsuleOffsetPx(
    index: Int,
    rowWidthPx: Float,
    itemCount: Int,
    insetPx: Float
): Float {
    if (index < 0 || itemCount <= 0) return 0f
    val slot = rowWidthPx / itemCount
    return slot * index + insetPx
}

/**
 * A fully rounded, centred pill floating over the page content, with an orange capsule that
 * slides between destinations.
 *
 * @param onSelect navigation callback, so the component does not own a NavController's policy.
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
    val selectedIndex = selectedNavIndex(currentRoute, destinations)

    val reduceMotion = rememberReduceMotion()
    val activeColor = MaterialTheme.colorScheme.primary
    val onActiveColor = MaterialTheme.colorScheme.onPrimary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant

    var rowWidthPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val insetPx = with(density) { 4.dp.toPx() }
    val targetOffset = activeCapsuleOffsetPx(selectedIndex, rowWidthPx, destinations.size, insetPx)
    // A spring rather than a tween: the capsule should settle with a little weight, and when the
    // user taps two tabs quickly it has to interpolate from where it is, not restart. With the
    // system animator scale at zero the capsule jumps instead - the indicator still shows the
    // right destination, which is the only thing it is for.
    val animatedOffset by animateFloatAsState(
        targetValue = targetOffset,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "navCapsuleOffset"
    )
    val capsuleOffset = if (reduceMotion) targetOffset else animatedOffset

    Surface(
        shape = RoundedCornerShape(BalajiDesign.RadiusPill),
        // The single line that decides whether this control is usable in light mode.
        color = MaterialTheme.colorScheme.surface.copy(alpha = BalajiDesign.GlassAlpha),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 8.dp,
        modifier = modifier
            .padding(horizontal = 16.dp)
            .widthIn(max = 480.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(NAV_PILL_HEIGHT_DP.dp)
                .padding(horizontal = 8.dp)
                .onSizeChanged { rowWidthPx = it.width.toFloat() }
        ) {
            // The sliding indicator sits under the items so its own hit box cannot steal a tap.
            if (selectedIndex >= 0 && rowWidthPx > 0f) {
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                capsuleOffset.roundToInt(),
                                with(density) { NAV_PILL_INNER_VERTICAL_PADDING_DP.dp.roundToPx() }
                            )
                        }
                        .width(with(density) { (rowWidthPx / destinations.size - insetPx * 2).toDp() })
                        .fillMaxHeight()
                        .heightIn(min = 48.dp)
                        .background(activeColor, RoundedCornerShape(BalajiDesign.RadiusPill))
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                destinations.forEachIndexed { index, item ->
                    val selected = index == selectedIndex
                    val tint = if (selected) onActiveColor else inactiveColor
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 2.dp, vertical = NAV_PILL_INNER_VERTICAL_PADDING_DP.dp)
                            .clip(RoundedCornerShape(BalajiDesign.RadiusPill))
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
                                    Badge(containerColor = BalajiDesign.Highlight) {
                                        Text(
                                            text = if (cartCount > 99) "99+" else cartCount.toString(),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            ) {
                                Icon(
                                    item.icon,
                                    // Decorative: the visible label below already names the tab,
                                    // and describing both makes TalkBack say it twice.
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
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
