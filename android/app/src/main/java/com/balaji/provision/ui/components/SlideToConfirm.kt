package com.balaji.provision.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoubleArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.balaji.provision.ui.theme.BalajiDesign
import kotlin.math.roundToInt

/**
 * The slide-to-confirm control used for the two actions that spend money or commit an order.
 *
 * A gesture is used because a thumb-sized orange button at the bottom of a cart is easy to hit by
 * accident, and an accidental order has to be cancelled by a person. The cost of that safety is
 * that a drag is not a tap, so the control has to offer a real alternative: TalkBack and Switch
 * Access get an `onClick` accessibility action that performs the same callback, and it is the
 * identical callback, not a second code path that can drift.
 *
 * What this component refuses to do:
 * - it never claims success. `Success` is a state the caller sets after the server has answered.
 * - it never fires twice. Once the thumb passes the threshold the track locks until the caller
 *   clears the submitting state, so a held finger cannot re-trigger the RPC.
 * - it does not decide whether the action is allowed. The caller passes `enabled` from the same
 *   server-derived conditions that gate the button it replaced.
 */

/** Where the control is in its life cycle. Only the caller can move it out of [Submitting]. */
enum class SlideConfirmState { Idle, Submitting, Success }

/**
 * How far along the track the thumb is, 0..1.
 *
 * Zero travel returns 0 rather than dividing by it: the first frame measures the track in
 * `onSizeChanged`, so a not-yet-laid-out control must read as "not slid", never as NaN or Infinity
 * that would compare greater than the threshold.
 */
fun slideProgress(offsetPx: Float, travelPx: Float): Float =
    if (travelPx <= 0f) 0f else (offsetPx / travelPx).coerceIn(0f, 1f)

/**
 * Whether the drag has travelled far enough to count as intentional.
 *
 * Requires positive travel for the same reason as [slideProgress], and compares against the
 * fraction rather than a pixel distance so the threshold means the same thing on a 360 dp phone
 * and an 840 dp tablet.
 */
fun shouldCommit(offsetPx: Float, travelPx: Float, commitFraction: Float): Boolean =
    travelPx > 0f && slideProgress(offsetPx, travelPx) >= commitFraction

/** The caption in the middle of the track. */
fun slideLabelFor(state: SlideConfirmState, label: String, successLabel: String?): String =
    when (state) {
        SlideConfirmState.Success -> successLabel ?: label
        else -> label
    }

/**
 * @param label what the gesture does, e.g. "Slide to Place Order". The brief is explicit that this
 *   wording has to match reality: "Slide to Checkout" while steps remain, "Slide to Place Order"
 *   only where the gesture really submits.
 * @param onConfirm called once per completed gesture, and by the accessibility action.
 * @param commitFraction fraction of the travel that counts as a deliberate slide.
 * @param successLabel shown after the caller has confirmed the server accepted the action.
 */
@Composable
fun SlideToConfirm(
    label: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    state: SlideConfirmState = SlideConfirmState.Idle,
    commitFraction: Float = BalajiDesign.SlideCommitFraction,
    successLabel: String? = null,
    sublabel: String? = null
) {
    val reduceMotion = rememberReduceMotion()
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current

    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    var offsetPx by remember { mutableFloatStateOf(0f) }
    var locked by remember { mutableStateOf(false) }
    // True only while a finger is on the track. While dragging, the thumb has to track the finger
    // with no animation; the moment the finger lifts, the animated value takes over so an early
    // release glides home instead of snapping.
    var dragging by remember { mutableStateOf(false) }

    val thumbSizePx = with(density) { SlideToConfirmMetrics.ThumbSize.roundToPx() }
    val insetPx = with(density) { SlideToConfirmMetrics.Inset.roundToPx() }
    val travelPx = (trackWidthPx - thumbSizePx - insetPx * 2f).coerceAtLeast(0f)

    val interactive = enabled && state == SlideConfirmState.Idle && !locked
    // A caller disables the control while it is working, because "can I act again" is false at
    // that moment. Rendering that as grey would make a submitted order look broken, so the fill
    // follows the state as well as the flag: busy and done keep the orange.
    val filled = enabled || state != SlideConfirmState.Idle

    // Releasing early brings the thumb home; a committed gesture parks it at the end and keeps it
    // there while the server is working. The previous version sent it home on commit too, so a
    // successful slide visibly rewound to the start and then jumped forward when the answer
    // arrived - the control looked like it had rejected the gesture.
    val targetOffset = when {
        state == SlideConfirmState.Success -> travelPx
        locked || state == SlideConfirmState.Submitting -> travelPx
        !enabled -> 0f
        else -> offsetPx
    }
    val animatedTarget by animateFloatAsState(
        targetValue = targetOffset,
        animationSpec = if (reduceMotion) {
            tween(0)
        } else {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        },
        label = "slideThumb"
    )
    val drawnOffset = if (dragging) offsetPx else animatedTarget
    // The wash follows the drawn thumb, not the raw drag offset, so it glides back with the
    // circle on an early release instead of vanishing in one frame.
    val progress = slideProgress(drawnOffset, travelPx)

    LaunchedEffect(state) {
        if (state != SlideConfirmState.Submitting) locked = false
        if (state == SlideConfirmState.Idle) offsetPx = 0f
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(SlideToConfirmMetrics.TrackHeight)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                if (!enabled && state == SlideConfirmState.Idle) disabled()
                stateDescription = when {
                    state == SlideConfirmState.Submitting -> "Working"
                    state == SlideConfirmState.Success -> "Done"
                    !enabled -> "Not available"
                    else -> "Slide right, or double-tap to activate"
                }
                role = Role.Button
                // The accessible equivalent of the gesture: the same callback, no drag required.
                onClick(label = label) {
                    if (interactive) {
                        locked = true
                        onConfirm()
                        true
                    } else {
                        false
                    }
                }
            },
        shape = RoundedCornerShape(BalajiDesign.RadiusPill),
        color = if (filled) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { trackWidthPx = it.width.toFloat() }
                .pointerInput(enabled, state, locked, travelPx) {
                    if (!interactive) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = {
                            dragging = false
                            if (shouldCommit(offsetPx, travelPx, commitFraction)) {
                                locked = true
                                haptics.performHapticFeedback(
                                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                                )
                                onConfirm()
                            } else {
                                offsetPx = 0f
                            }
                        },
                        onDragCancel = {
                            dragging = false
                            offsetPx = 0f
                        }
                    ) { change, dragX ->
                        change.consume()
                        offsetPx = (offsetPx + dragX).coerceIn(0f, travelPx)
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            // The filled portion grows with the thumb, so the distance left to travel is legible.
            if (progress > 0f) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(insetPx, insetPx) }
                        .width(with(density) { (drawnOffset + thumbSizePx).toDp() })
                        // The wash is the thumb's height, not the track's. Filling the whole
                        // height started it 4 dp down and clipped its bottom edge on the pill,
                        // which showed as a grey band under the circle while dragging.
                        .height(SlideToConfirmMetrics.ThumbSize)
                        .background(
                            Color.White.copy(alpha = 0.18f),
                            RoundedCornerShape(BalajiDesign.RadiusPill)
                        )
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = SlideToConfirmMetrics.TextStart),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(BalajiDesign.InnerGap)
            ) {
                Spacer(modifier = Modifier.width(SlideToConfirmMetrics.ThumbSize))
                Text(
                    text = slideLabelFor(state, label, successLabel),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (filled) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (sublabel != null && state == SlideConfirmState.Idle) {
                    Text(
                        text = sublabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (insetPx + drawnOffset).roundToInt(),
                            ((with(density) { SlideToConfirmMetrics.TrackHeight.toPx() } -
                                thumbSizePx) / 2f).roundToInt()
                        )
                    }
                    .size(SlideToConfirmMetrics.ThumbSize)
                    // A 2 dp lift: on the orange fill the white circle otherwise reads as a hole
                    // punched in the pill rather than a handle resting on it.
                    .shadow(2.dp, CircleShape)
                    .background(
                        if (filled) Color.White else MaterialTheme.colorScheme.surface,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                when (state) {
                    SlideConfirmState.Success -> Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )

                    SlideConfirmState.Submitting -> CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )

                    SlideConfirmState.Idle -> Icon(
                        Icons.Default.DoubleArrow,
                        contentDescription = null,
                        tint = if (enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

/**
 * Geometry of the track, from the reference board.
 *
 * The thumb is a 48 dp touch target, so the control is operable by thumb even though the track is
 * 56 dp tall; the mockups' tighter proportions were measured and lose the accessibility floor.
 */
object SlideToConfirmMetrics {
    val TrackHeight = 56.dp
    val ThumbSize = 48.dp
    val Inset = 4.dp
    val TextStart = 60.dp
}
