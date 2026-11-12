package com.balaji.provision.ui.components

import android.graphics.Bitmap
import coil.size.Size
import coil.transform.Transformation

/** Display-only alpha trim: transparent square canvas margins must not make wide packs float. */
object TrimProductTransparency : Transformation {
    override val cacheKey = "product-alpha-bounds-v1"
    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        if (!input.hasAlpha()) return input
        val pixels = IntArray(input.width * input.height)
        input.getPixels(pixels, 0, input.width, 0, 0, input.width, input.height)
        val bounds = visibleProductBounds(pixels, input.width, input.height) ?: return input
        if (bounds.left == 0 && bounds.top == 0 && bounds.width == input.width && bounds.height == input.height) return input
        return Bitmap.createBitmap(input, bounds.left, bounds.top, bounds.width, bounds.height)
    }
}

internal data class ProductBounds(val left: Int, val top: Int, val width: Int, val height: Int)
internal fun visibleProductBounds(pixels: IntArray, width: Int, height: Int): ProductBounds? {
    require(width > 0 && height > 0 && pixels.size == width * height)
    var left = width; var top = height; var right = -1; var bottom = -1
    pixels.forEachIndexed { i, pixel ->
        if ((pixel ushr 24) > 8) {
            val x = i % width; val y = i / width
            left = minOf(left, x); right = maxOf(right, x)
            top = minOf(top, y); bottom = maxOf(bottom, y)
        }
    }
    return if (right < left) null else ProductBounds(left, top, right-left+1, bottom-top+1)
}
