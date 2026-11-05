package com.balaji.provision.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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

/**
 * The shared translucent surfaces of the customer app.
 *
 * "Glass" here means a near-opaque tinted fill with a hairline border and low elevation. It
 * deliberately does not mean a blur: `Modifier.blur` on a scrolling product grid costs frames on
 * the mid-range phone this store actually runs on, and the reference board's cards read as flat
 * warm surfaces anyway. If a screen ever needs real backdrop blur, it has to be measured first,
 * not assumed.
 */

/** A glass panel with rounded corners, a hairline outline and a low shadow. */
@Composable
fun BalajiGlassSurface(
    modifier: Modifier = Modifier,
    alpha: Float = BalajiDesign.GlassAlpha,
    cornerRadius: androidx.compose.ui.unit.Dp = BalajiDesign.RadiusCard,
    border: BorderStroke = BorderStroke(
        BalajiDesign.CardBorder,
        MaterialTheme.colorScheme.outlineVariant
    ),
    tonalElevation: androidx.compose.ui.unit.Dp = 0.dp,
    shadowElevation: androidx.compose.ui.unit.Dp = BalajiDesign.CardElevation,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = MaterialTheme.colorScheme.surface.copy(alpha = alpha),
        border = border,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation
    ) {
        Box(content = content)
    }
}

/**
 * A glass card in peach, used where a surface has to read as informational rather than as an
 * action: the store-closed panel, a savings note, a delivery summary.
 *
 * It is never used for a purchasing control. Orange fill means "this buys something" in this app,
 * and diluting that is worse than a slightly less pretty card.
 */
@Composable
fun BalajiGlassCard(
    modifier: Modifier = Modifier,
    background: Color = BalajiDesign.PeachBackground,
    contentPadding: PaddingValues = PaddingValues(BalajiDesign.ScreenPadding),
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(BalajiDesign.RadiusCard),
        color = background,
        border = BorderStroke(BalajiDesign.CardBorder, BalajiDesign.PeachSurface),
        shadowElevation = BalajiDesign.CardElevation
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BalajiDesign.InnerGap),
            content = content
        )
    }
}

/**
 * The one orange purchasing button. Solid fill, no gradient, no glow.
 *
 * The reference board renders ADD and Add to Cart as flat orange, and the brief calls out
 * "simple SOLID orange" specifically because metallic effects on a grocery list read as
 * advertising rather than as a control.
 */
@Composable
fun BalajiOrangeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: androidx.compose.ui.unit.Dp = BalajiDesign.PrimaryCtaHeight,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp),
    leading: (@Composable () -> Unit)? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(height),
        shape = RoundedCornerShape(BalajiDesign.RadiusPill),
        contentPadding = contentPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 2.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp
        )
    ) {
        leading?.invoke()
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}
