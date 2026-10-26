package com.balaji.smsgateway

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun BalajiGatewayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF0B5D3B),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFB8F0D0),
            onPrimaryContainer = Color(0xFF00210F),
            secondary = Color(0xFF4F6354),
            background = Color(0xFFF7FBF6),
            surface = Color(0xFFF7FBF6),
            error = Color(0xFFB3261E),
        ),
        content = content,
    )
}
