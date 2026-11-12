package com.balaji.provision.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

data class PodiumColors(val background: Color,val topLight: Color,val top: Color,val edge: Color,val edgeBottom: Color,val rim: Color,val glow: Color,val shadow: Color)
private val LightPodium = PodiumColors(LightBackground,BrandOrangeContainer,Color(0xFFFFB77B),BrandOrange,BrandOrangeDeep,Color(0xFFFFE9D9),BrandOrange.copy(alpha=.16f),Color(0xFF592400))
private val DarkPodium = PodiumColors(DarkBackground,Color(0xFFAC571F),Color(0xFF873600),Color(0xFFC44F00),Color(0xFF532400),Color(0xFFCC8548),BrandOrange.copy(alpha=.09f),Color.Black)
val MaterialTheme.podiumColors: PodiumColors
    @Composable get() = if(colorScheme.background.luminance()<.3f) DarkPodium else LightPodium
