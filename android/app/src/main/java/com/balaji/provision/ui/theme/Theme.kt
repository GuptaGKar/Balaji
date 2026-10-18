package com.balaji.provision.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = BlinkitGreen,
    onPrimary = Color.White,
    primaryContainer = BlinkitGreenContainer,
    onPrimaryContainer = BlinkitGreenDark,
    secondary = BlinkitYellow,
    onSecondary = Color.Black,
    secondaryContainer = BlinkitYellowLight,
    onSecondaryContainer = BlinkitYellowDark,
    background = BackgroundLight,
    onBackground = TextPrimary,
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = TextSecondary,
    outline = BorderLight
)

@Composable
fun BalajiTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}