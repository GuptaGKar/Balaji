package com.balaji.provision.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.balaji.provision.ui.theme.BalajiDesign
import com.balaji.provision.ui.theme.BalajiText
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
 * The tab's icon, with the basket count on the one destination that needs it.
 *
 * Extracted because the selected and unselected forms lay the icon out differently - beside the
 * label in one, above it in the other - and a badge that only appeared in one of the two would
 * mean the count disappeared the moment you tapped Basket.
 *
 * The icon itself is decorative: the label names the tab, and describing both makes TalkBack say
 * it twice.
 */
@Composable
private fun TabIcon(item: NavItem, tint: Color, cartCount: Int) {
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
            Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
    } else {
        Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/**
 * Horizontal offset and width of the active capsule, in pixels.
 *
 * Pure, because this is the arithmetic that decides whether the indicator lands on the right tab.
 * The capsule is sized to the *slot* - row width divided by the number of destinations, inset by
 * 4 dp on each side - and that is deliberate: an earlier attempt sized it to the selected item's
 * content so it could hold the icon and label side by side, and on this app's four destinations a
 * slot is 78 dp while "Categories" beside its icon needs about 100 dp. The label truncated.
 *
 * A negative or unmeasured row returns a zero-width capsule rather than a negative one, because
 * `Modifier.width` rejects negative dp and the first frame has not measured yet.
 */
fun capsuleGeometryPx(
    index: Int,
    rowWidthPx: Float,
    itemCount: Int,
    insetPx: Float
): Pair<Float, Float> {
    if (index < 0 || itemCount <= 0 || rowWidthPx <= 0f) return 0f to 0f
    val slot = rowWidthPx / itemCount
    val width = slot - insetPx * 2f
    if (width <= 0f) return 0f to 0f
    return (slot * index + insetPx) to width
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
    // Measured on the A34 rather than chosen: at 11sp on the 0.94 surface (luminance 248 of 255),
    // onSurfaceVariant #757575 comes out at 4.35:1, which is under WCAG AA's 4.5:1 for text this
    // small. onSurface #252525 is 14.4:1. The reference board's inactive labels are dark, not grey,
    // so this follows the mockup and the guideline instead of trading one for the other.
    val inactiveColor = MaterialTheme.colorScheme.onSurface

    var rowWidthPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val insetPx = with(density) { 4.dp.toPx() }
    val (targetLeft, targetWidth) = capsuleGeometryPx(selectedIndex, rowWidthPx, destinations.size, insetPx)
    // A spring rather than a tween: the capsule should settle with a little weight, and when the
    // user taps two tabs quickly it has to interpolate from where it is, not restart. With the
    // system animator scale at zero the capsule jumps instead - the indicator still shows the
    // right destination, which is the only thing it is for.
    val animatedLeft by animateFloatAsState(
        targetValue = targetLeft,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "navCapsuleLeft"
    )
    val capsuleLeft = if (reduceMotion) targetLeft else animatedLeft

    val pillShape = RoundedCornerShape(BalajiDesign.RadiusPill)
    Surface(
        shape = pillShape,
        // The single line that decides whether this control is usable in light mode. The colour
        // is painted by liquidGlass rather than by the Surface, so the rim and the top specular
        // band land on the fill and under the labels in the right order.
        color = Color.Transparent,
        shadowElevation = 8.dp,
        modifier = modifier
            .padding(horizontal = 16.dp)
            .widthIn(max = 480.dp)
            .liquidGlass(
                shape = pillShape,
                fill = MaterialTheme.colorScheme.surface.copy(alpha = BalajiDesign.GlassAlpha)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(NAV_PILL_HEIGHT_DP.dp)
                .padding(horizontal = 8.dp)
                .onSizeChanged { rowWidthPx = it.width.toFloat() }
        ) {
            // The indicator is drawn in a layer that cannot influence the pill's size and cannot
            // escape it: `matchParentSize`, then the vertical inset applied before the background.
            // The previous version offset the capsule *down* by the inner padding from inside an
            // already-padded box, which pushed the orange past the pill's bottom edge - visible in
            // a screenshot, and not visible to any unit test.
            if (selectedIndex >= 0 && targetWidth > 0f) {
                Box(modifier = Modifier.matchParentSize()) {
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(capsuleLeft.roundToInt(), 0) }
                            .width(with(density) { targetWidth.toDp() })
                            .fillMaxHeight()
                            .padding(vertical = NAV_PILL_INNER_VERTICAL_PADDING_DP.dp)
                            // The capsule gets the same treatment as the pill around it: its
                            // orange is painted as a layered gradient with a light rim, so the
                            // active tab reads as a piece of the same glass rather than a flat
                            // sticker on it. It stays orange-on-white text - BUG-028's accepted
                            // contrast is unchanged by the rim.
                            .liquidGlass(
                                shape = RoundedCornerShape(BalajiDesign.RadiusPill),
                                fill = activeColor
                            )
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                destinations.forEachIndexed { index, item ->
                    val selected = index == selectedIndex
                    val tint = if (selected) onActiveColor else inactiveColor
                    // Icon over label for every destination, selected or not.
                    //
                    // The reference board draws the selected tab with the icon beside the label,
                    // and that form was tried here: it cannot work on this app's four destinations.
                    // Each slot is (screen - 32 dp margin - 16 dp inner padding) / 4, which is 78 dp
                    // on a 360 dp phone, and "Categories" next to its icon needs about 100 dp. The
                    // label ellipsised to "Categ…" - the pill clipped the one word that tells you
                    // where you are, and the brief's own acceptance list forbids clipped text. A
                    // wider capsule around a truncated word is not the reference; it is the same
                    // reference with a defect added.
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(BalajiDesign.RadiusPill))
                            .selectable(
                                selected = selected,
                                onClick = { onSelect(item.route) },
                                role = Role.Tab
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        TabIcon(item, tint, cartCount)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.label,
                            color = tint,
                            style = BalajiText.Nav,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
