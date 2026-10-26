package com.balaji.provision.ui.splash

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.balaji.provision.ui.components.BalajiBasketMark
import com.balaji.provision.ui.theme.BrandOrange
import com.balaji.provision.ui.theme.BrandOrangeDeep

/**
 * Branded launch screen. It is layered over the live app rather than blocking
 * startup, so navigation and data loading begin immediately underneath.
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(BrandOrange, BrandOrangeDeep)
                )
            )
    ) {
        SplashPattern()

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            BalajiBasketMark(
                modifier = Modifier.size(132.dp),
                basketColor = Color.White,
                leafColor = Color.White
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Balaji",
                    color = Color.White,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Provision Store",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Text(
                text = "Freshness at your doorstep",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 18.sp,
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Center
            )
        }

        Text(
            text = "Fresh groceries • Daily essentials • Local delivery",
            color = Color.White.copy(alpha = 0.75f),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
        )
    }
}

/** Very subtle produce outline pattern behind the splash content. */
@Composable
private fun SplashPattern() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val stroke = 2f
        val faint = Color.White.copy(alpha = 0.08f)
        val radius = size.minDimension * 0.19f

        drawCircle(
            color = faint,
            radius = radius,
            center = Offset(size.width * 0.08f, size.height * 0.14f),
            style = Stroke(width = stroke)
        )
        drawCircle(
            color = faint,
            radius = radius * 1.6f,
            center = Offset(size.width * 0.95f, size.height * 0.28f),
            style = Stroke(width = stroke)
        )
        drawCircle(
            color = faint,
            radius = radius * 1.2f,
            center = Offset(size.width * 0.12f, size.height * 0.82f),
            style = Stroke(width = stroke)
        )
        drawCircle(
            color = faint,
            radius = radius * 0.8f,
            center = Offset(size.width * 0.9f, size.height * 0.9f),
            style = Stroke(width = stroke)
        )
    }
}
