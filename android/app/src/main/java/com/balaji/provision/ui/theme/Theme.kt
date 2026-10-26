package com.balaji.provision.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = BrandOrange,
    onPrimary = Color.White,
    primaryContainer = BrandOrangeContainer,
    onPrimaryContainer = BrandOrangeOnContainer,
    inversePrimary = BrandOrangeLight,
    secondary = BrandAmber,
    onSecondary = Color(0xFF241A00),
    secondaryContainer = BrandAmberContainer,
    onSecondaryContainer = BrandAmberOnContainer,
    tertiary = Color(0xFF1D4ED8),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDBEAFE),
    onTertiaryContainer = Color(0xFF1E3A8A),
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = Color.White,
    onSurface = LightOnSurface,
    surfaceVariant = Color(0xFFF7F7F5),
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = Color(0xFFD93025),
    onError = Color.White,
    errorContainer = Color(0xFFFCE8E6),
    onErrorContainer = Color(0xFF8C1D18),
    inverseSurface = Color(0xFF2A313A),
    inverseOnSurface = Color(0xFFEFF2F6),
    scrim = Color(0xFF000000)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB547),
    onPrimary = Color(0xFF3A1500),
    primaryContainer = Color(0xFF8A3B00),
    onPrimaryContainer = Color(0xFFFFDCC2),
    inversePrimary = BrandOrange,
    secondary = Color(0xFFF5C518),
    onSecondary = Color(0xFF2B2200),
    secondaryContainer = Color(0xFF4A3A00),
    onSecondaryContainer = Color(0xFFFFE08A),
    tertiary = Color(0xFF93C5FD),
    onTertiary = Color(0xFF0B1F45),
    tertiaryContainer = Color(0xFF1E3A8A),
    onTertiaryContainer = Color(0xFFDBEAFE),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE5E9EF),
    inverseOnSurface = Color(0xFF2A313A),
    scrim = Color(0xFF000000)
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

/** Semantic (non-Material) colours for the current theme. */
val MaterialTheme.appColors: AppColors
    @Composable
    get() = LocalAppColors.current

@Composable
fun BalajiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val appColors = if (darkTheme) DarkAppColors else LightAppColors

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BalajiTypography,
            shapes = BalajiShapes,
            content = content
        )
    }
}
