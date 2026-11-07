package com.balaji.provision.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.balaji.provision.ui.theme.BalajiDesign

/**
 * The ADD to quantity-stepper control, and the one place its motion is defined.
 *
 * Three states, matching what the reference board shows: a solid orange ADD pill, a brief
 * acknowledgement while the cart write settles, and `[ −  n  + ]` which collapses back to ADD at
 * zero. The number slides in the direction of the change - up adds from the right, down from the
 * left - because a digit that simply swaps is indistinguishable from a digit that failed to
 * update.
 *
 * What this does *not* do: it never decides what a quantity means. The caller owns the cart
 * write, the stock ceiling and the weighted picker, and this only reflects the number it is
 * given. Animation must not be the thing that changes a cart.
 */

/** Whether the control shows ADD or the stepper. */
enum class AddControlMode { Add, Stepper }

/** Which way the quantity digit should travel, or not at all. */
enum class QuantitySlide { None, Increase, Decrease }

/**
 * Stepper at any quantity above zero, ADD at zero.
 *
 * Zero is the only collapse point, and it is the same rule the cart uses to drop a line, so the
 * control cannot show `[ − 0 + ]` while the item is already gone.
 */
fun addControlModeFor(quantityBase: Long): AddControlMode =
    if (quantityBase > 0L) AddControlMode.Stepper else AddControlMode.Add

/**
 * Direction of the change between two quantities.
 *
 * Equal values return None so a recomposition that repeats the same number - which happens on
 * every cart refresh - does not animate a digit that did not change.
 */
fun quantitySlide(previous: Long, current: Long): QuantitySlide = when {
    current > previous -> QuantitySlide.Increase
    current < previous -> QuantitySlide.Decrease
    else -> QuantitySlide.None
}

/**
 * Horizontal offset a digit enters from, given the slide direction.
 *
 * Positive means "from the right". Returning zero for None keeps a static swap from sliding in
 * from a position that would imply a change that did not happen.
 */
fun digitSlideOffset(slide: QuantitySlide, widthPx: Int): Int = when (slide) {
    QuantitySlide.Increase -> widthPx
    QuantitySlide.Decrease -> -widthPx
    QuantitySlide.None -> 0
}

/**
 * How many steps of this variant are in the basket.
 *
 * The cart stores base units - grams for a 250 g min-weight item, packs for a 500 g packet - and
 * the stepper moves one step at a time, so the digit has to show steps or "1 pack" reads as "1
 * gram". A quantity that is not a whole number of steps is shown as it is rather than rounded:
 * a partial step is a real state (a weighted line settled at 470 g) and inventing "2" would
 * misreport the basket.
 */
fun stepCount(quantityBase: Long, stepQty: Long): Long =
    if (stepQty > 0L && quantityBase % stepQty == 0L) quantityBase / stepQty else quantityBase

/**
 * @param quantityBase the cart's quantity in base units for this exact variant.
 * @param stepQty the quantity one plus/minus tap moves; also the divisor for the displayed digit.
 * @param onAdd pressed when the product has no cart line yet.
 * @param onIncrement pressed to add one step.
 * @param onDecrement pressed to remove one step; at the last step the caller removes the line and
 *   the control collapses back to ADD.
 * @param canIncrease false at the stock or maximum-order ceiling; the plus stays visible and
 *   disabled rather than disappearing, so the control does not change width on a stock edge.
 *
 * The two stepper buttons are 48 dp squares, so they meet the touch floor outright. The ADD pill
 * is 40 dp tall by design - the brief allows 36-44 dp "with expanded hitbox if visually smaller" -
 * and whether its effective hit box is 40 dp or 48 dp depends on how this Compose version applies
 * `minimumInteractiveComponentSize` inside a fixed-height parent. That has to be measured on the
 * device, not asserted here.
 */
@Composable
fun AnimatedAddQuantityControl(
    quantityBase: Long,
    onAdd: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    productName: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    canIncrease: Boolean = true,
    stepQty: Long = 1L,
    /**
     * Horizontal inset behind the ADD label. Narrow browse cards pass a smaller value so the
     * pill fits on the same 128 dp line as the price; wide rows keep the default.
     */
    inset: androidx.compose.ui.unit.Dp = 18.dp
) {
    val reduceMotion = rememberReduceMotion()
    val mode = addControlModeFor(quantityBase)

    // The previous quantity is remembered rather than derived, so a recomposition that repeats
    // the same value animates nothing.
    var previousQuantity by remember { mutableStateOf(quantityBase) }
    val slide = if (reduceMotion) QuantitySlide.None else quantitySlide(previousQuantity, quantityBase)
    LaunchedEffect(quantityBase) { previousQuantity = quantityBase }

    val stateLabel = when (mode) {
        AddControlMode.Add -> if (enabled) "Not added" else "Out of stock"
        AddControlMode.Stepper -> "$quantityBase in basket"
    }
    val digit = stepCount(quantityBase, stepQty)

    // The wrapper carries what the control is and what it currently holds. It deliberately does NOT
    // merge its descendants: Compose UI 1.5.4 has no plusAction/minusAction, so a merged node would
    // leave a screen-reader customer hearing "3 in basket" with no way to change it. Keeping the
    // buttons in the tree, each with its own label, is the version of this that actually works.
    Box(
        modifier = modifier
            .heightIn(min = BalajiDesign.MinTouchTarget)
            .height(BalajiDesign.ControlHeight)
            .semantics {
                contentDescription = productName
                stateDescription = stateLabel
            },
        contentAlignment = Alignment.CenterEnd
    ) {
        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                if (reduceMotion) {
                    fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                } else {
                    // The pill grows into the stepper and shrinks back, so the width change is
                    // animated rather than snapping the row it sits in.
                    (fadeIn(tween(BalajiDesign.Motion.Expand / 2)) +
                        slideInHorizontally(
                            animationSpec = tween(BalajiDesign.Motion.Expand),
                            initialOffsetX = { it / 4 }
                        )) togetherWith
                        (fadeOut(tween(BalajiDesign.Motion.Expand / 2)) +
                            slideOutHorizontally(
                                animationSpec = tween(BalajiDesign.Motion.Expand),
                                targetOffsetX = { it / 4 }
                            )) using SizeTransform(clip = false)
                }
            },
            label = "addControlMode"
        ) { currentMode ->
            when (currentMode) {
                AddControlMode.Add -> Button(
                    onClick = onAdd,
                    enabled = enabled,
                    modifier = Modifier.height(BalajiDesign.ControlHeight),
                    shape = RoundedCornerShape(BalajiDesign.RadiusPill),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = inset,
                        vertical = 0.dp
                    ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp
                    )
                ) {
                    Text(
                        text = "ADD",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                AddControlMode.Stepper -> Surface(
                    shape = RoundedCornerShape(BalajiDesign.RadiusPill),
                    color = Color.Transparent,
                    // The orange hairline marks the stepper as an active control; the fill
                    // under it is glass so it matches the browse card it sits on.
                    modifier = Modifier.liquidGlass(
                        shape = RoundedCornerShape(BalajiDesign.RadiusPill),
                        fill = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        BalajiDesign.CardBorder,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    ) {
                        IconButton(
                            onClick = onDecrement,
                            modifier = Modifier.size(BalajiDesign.MinTouchTarget)
                        ) {
                            Icon(
                                Icons.Default.Remove,
                                contentDescription = "Remove one from ${productName}",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Box(
                            modifier = Modifier.width(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedContent(
                                targetState = digit,
                                transitionSpec = {
                                    if (reduceMotion) {
                                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                                    } else {
                                        val direction = slide
                                        (slideInHorizontally(
                                            animationSpec = tween(BalajiDesign.Motion.NumberSlide),
                                            initialOffsetX = { digitSlideOffset(direction, it) }
                                        ) + fadeIn(tween(BalajiDesign.Motion.NumberSlide))) togetherWith
                                            (slideOutHorizontally(
                                                animationSpec = tween(BalajiDesign.Motion.NumberSlide),
                                                targetOffsetX = {
                                                    -digitSlideOffset(direction, it)
                                                }
                                            ) + fadeOut(tween(BalajiDesign.Motion.NumberSlide)))
                                    }
                                },
                                label = "quantityDigit"
                            ) { quantity ->
                                Text(
                                    text = quantity.toString(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                            }
                        }

                        IconButton(
                            onClick = onIncrement,
                            enabled = canIncrease,
                            modifier = Modifier.size(BalajiDesign.MinTouchTarget)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Add one more to $productName",
                                tint = if (canIncrease) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                },
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
