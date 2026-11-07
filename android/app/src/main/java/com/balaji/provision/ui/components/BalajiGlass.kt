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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.balaji.provision.ui.theme.BalajiDesign

/**
 * The shared translucent surfaces of the customer app.
 *
 * "Glass" here means a near-opaque tinted fill with a liquid rim and low elevation. It
 * deliberately does not mean a blur: `Modifier.blur` blurs a surface's own drawing rather than
 * what is behind it, real backdrop blur needs a captured backdrop layer or a third-party
 * library, and the brief forbids blur shaders in scrolling grids. The "liquid" is therefore
 * drawn - gradient rim, top specular band, bottom shade - by [liquidGlass], which also paints
 * the fill when [Modifier.liquidGlass] is given one.
 */

/**
 * The liquid-glass treatment: gradient fill (optional), rim, specular top band and bottom shade.
 *
 * Apply this to a `Surface`/`Card`'s modifier **and** give that container a transparent colour
 * when [fill] is provided - the fill is painted here so that the rim and highlight can be layered
 * over it and under the content in the right order.
 *
 * Everything is drawn inside a clip of [shape] and never uses blur: one cached path, two
 * gradients and a stroke per frame, which is the same order of cost as the shadow that is
 * already there.
 *
 * @param fill the surface colour to paint as a layered vertical gradient, or null to leave the
 *   container's own colour alone and paint only the rim and highlights.
 */
fun Modifier.liquidGlass(
    shape: RoundedCornerShape,
    fill: Color? = null
): Modifier = this.drawWithContent {
    val glass = BalajiDesign.LiquidGlass
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply {
        when (outline) {
            is Outline.Rectangle -> addRect(outline.rect)
            is Outline.Rounded -> addRoundRect(outline.roundRect)
            is Outline.Generic -> addPath(outline.path)
            else -> addRect(size.toRect())
        }
    }

    if (fill != null) {
        val (topAlpha, bottomAlpha) = glass.fillAlphas(fill.alpha)
        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                colors = listOf(fill.copy(alpha = topAlpha), fill.copy(alpha = bottomAlpha)),
                startY = 0f,
                endY = size.height
            )
        )
    }

    drawContent()

    // The rim, the specular band and the shade are painted over the content, so everything
    // happens inside a clip of the shape: a gradient rect would otherwise square off the
    // rounded corners it is meant to be following.
    clipPath(path) {
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colorStops = glass.RimStops.toTypedArray(),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            ),
            style = Stroke(width = glass.RimWidth.toPx())
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = glass.SpecularAlpha), Color.Transparent),
                startY = 0f,
                endY = glass.SpecularHeight.toPx()
            )
        )
        val shadeTop = (size.height - glass.ShadeHeight.toPx()).coerceAtLeast(0f)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = glass.ShadeAlpha)),
                startY = shadeTop,
                endY = size.height
            )
        )
    }
}

/** A glass panel with rounded corners, a liquid rim and a low shadow. */
@Composable
fun BalajiGlassSurface(
    modifier: Modifier = Modifier,
    alpha: Float = BalajiDesign.GlassAlpha,
    cornerRadius: androidx.compose.ui.unit.Dp = BalajiDesign.RadiusCard,
    border: BorderStroke? = null,
    tonalElevation: androidx.compose.ui.unit.Dp = 0.dp,
    shadowElevation: androidx.compose.ui.unit.Dp = BalajiDesign.CardElevation,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Surface(
        modifier = modifier.liquidGlass(
            shape = shape,
            fill = MaterialTheme.colorScheme.surface.copy(alpha = alpha)
        ),
        shape = shape,
        color = Color.Transparent,
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
        modifier = modifier.liquidGlass(
            shape = RoundedCornerShape(BalajiDesign.RadiusCard),
            fill = background
        ),
        shape = RoundedCornerShape(BalajiDesign.RadiusCard),
        color = Color.Transparent,
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
