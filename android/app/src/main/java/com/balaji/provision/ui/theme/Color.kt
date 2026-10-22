package com.balaji.provision.ui.theme

import androidx.compose.ui.graphics.Color

// Brand palette: fresh green primary with a warm amber accent, chosen to read
// as a neighbourhood provision store rather than a marketplace.
val BrandGreen = Color(0xFF0C831F)
val BrandGreenDeep = Color(0xFF075C14)
val BrandGreenContainer = Color(0xFFDCFCE7)
val BrandGreenOnContainer = Color(0xFF052E16)
val BrandGreenLight = Color(0xFF7BDC95)

val BrandAmber = Color(0xFFF7C104)
val BrandAmberDeep = Color(0xFF6B4E00)
val BrandAmberContainer = Color(0xFFFFF7DB)
val BrandAmberOnContainer = Color(0xFF241A00)

// Light neutrals
val LightBackground = Color(0xFFF6F7F9)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF1F3F7)
val LightOnSurface = Color(0xFF14181D)
val LightOnSurfaceVariant = Color(0xFF59636F)
val LightOutline = Color(0xFFD7DCE3)
val LightOutlineVariant = Color(0xFFE9EDF1)

// Dark neutrals (deliberately not pure black, per Material guidance)
val DarkBackground = Color(0xFF0F1216)
val DarkSurface = Color(0xFF171B21)
val DarkSurfaceVariant = Color(0xFF232932)
val DarkOnSurface = Color(0xFFE5E9EF)
val DarkOnSurfaceVariant = Color(0xFFB4BECA)
val DarkOutline = Color(0xFF3A424D)
val DarkOutlineVariant = Color(0xFF2A313A)

/**
 * Semantic colours for states that Material's roles do not cover
 * (stock, order status, delivery promise). Kept beside the Material scheme so
 * every screen reads from one source of truth in both light and dark mode.
 */
data class AppColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
    val onInfoContainer: Color
)

val LightAppColors = AppColors(
    success = Color(0xFF15803D),
    onSuccess = Color.White,
    successContainer = Color(0xFFDCFCE7),
    onSuccessContainer = Color(0xFF14532D),
    warning = Color(0xFFB45309),
    onWarning = Color.White,
    warningContainer = Color(0xFFFEF3C7),
    onWarningContainer = Color(0xFF78350F),
    info = Color(0xFF1D4ED8),
    onInfo = Color.White,
    infoContainer = Color(0xFFDBEAFE),
    onInfoContainer = Color(0xFF1E3A8A)
)

val DarkAppColors = AppColors(
    success = Color(0xFF4ADE80),
    onSuccess = Color(0xFF052E16),
    successContainer = Color(0xFF14532D),
    onSuccessContainer = Color(0xFFBBF7D0),
    warning = Color(0xFFFBBF24),
    onWarning = Color(0xFF3B2500),
    warningContainer = Color(0xFF453100),
    onWarningContainer = Color(0xFFFDE68A),
    info = Color(0xFF93C5FD),
    onInfo = Color(0xFF0B1F45),
    infoContainer = Color(0xFF1E3A8A),
    onInfoContainer = Color(0xFFDBEAFE)
)
