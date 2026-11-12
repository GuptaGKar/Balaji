package com.balaji.provision.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.balaji.provision.ui.theme.podiumColors

/** Static, independent UI layers. Coil caches only the original product asset. */
@Composable
fun ProductShowcasePodium(
    imageUrl: String?,
    productName: String,
    modifier: Modifier = Modifier,
    showPodium: Boolean = true
) {
    val colors = MaterialTheme.podiumColors
    val context = LocalContext.current
    val resolved = resolveProductImageUrl(imageUrl)
    val request = remember(resolved, context) {
        ImageRequest.Builder(context).data(resolved).allowHardware(false)
            .transformations(TrimProductTransparency).build()
    }
    BoxWithConstraints(
        modifier.clipToBounds().background(colors.background).clearAndSetSemantics {
            // One announcement for the whole stage: the drawn platform is decorative and the
            // loading/error placeholders are not content. The old details hero said the same.
            contentDescription = "Image of $productName"
        },
        contentAlignment = Alignment.Center
    ) {
        if (showPodium) Canvas(Modifier.fillMaxSize()) {
            val w=size.width; val h=size.height
            drawCircle(Brush.radialGradient(listOf(colors.glow, Color.Transparent), Offset(w*.5f,h*.48f),w*.5f),w*.5f,Offset(w*.5f,h*.48f))
            // Layered translucent ovals approximate a diffused shadow without a blur pass.
            for(i in 5 downTo 1) drawOval(colors.shadow.copy(alpha=.018f*i),Offset(w*(.12f-i*.008f),h*(.84f-i*.002f)),Size(w*(.76f+i*.016f),h*(.1f+i*.007f)))
            drawOval(Brush.verticalGradient(listOf(colors.edge,colors.edgeBottom),h*.77f,h*.92f),Offset(w*.12f,h*.77f),Size(w*.76f,h*.15f))
            drawRect(Brush.verticalGradient(listOf(colors.edge,colors.edgeBottom),h*.77f,h*.86f),Offset(w*.12f,h*.77f),Size(w*.76f,h*.075f))
            drawOval(Brush.verticalGradient(listOf(colors.topLight,colors.top),h*.69f,h*.85f),Offset(w*.12f,h*.69f),Size(w*.76f,h*.16f))
            drawOval(colors.rim,Offset(w*.12f,h*.69f),Size(w*.76f,h*.16f),style=Stroke(1.2.dp.toPx()))
        }
        // Fit inside a bottom-aligned box: bottles and wide cartons retain their own aspect ratio.
        // Prepared square canvases have 32px margins and are never stretched or cropped.
        Box(Modifier.fillMaxWidth(.82f).height(maxHeight * if(showPodium) .77f else .9f)
            .align(Alignment.TopCenter).padding(top=8.dp),contentAlignment=Alignment.BottomCenter) {
            if(resolved == null) Text(productName.take(1),color=MaterialTheme.colorScheme.onSurface)
            else SubcomposeAsyncImage(model=request,contentDescription=null,
                contentScale=ContentScale.Fit,alignment=Alignment.BottomCenter,
                loading={ Text("Loading product image",style=MaterialTheme.typography.labelSmall) },
                error={ Text("Image unavailable",style=MaterialTheme.typography.labelSmall) },
                modifier=Modifier.fillMaxSize())
        }
    }
}
