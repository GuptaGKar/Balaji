package com.balaji.provision.ui.components

import org.junit.Assert.*
import org.junit.Test

class ProductBoundsTest {
    @Test fun `transparent margins are trimmed for tall and wide package shapes without losing corners`() {
        // Shape matrix: atta bag, milk pouch, oil bottle, chips, biscuits, rice, soap, loose groceries.
        val shapes = listOf(6 to 8, 7 to 5, 3 to 9, 5 to 8, 9 to 3, 7 to 9, 6 to 4, 9 to 6)
        shapes.forEach { (w,h) ->
            val pixels = IntArray(12*12)
            for(y in 1..h) for(x in 1..w) pixels[y*12+x] = -1
            assertEquals(ProductBounds(1,1,w,h),visibleProductBounds(pixels,12,12))
            assertEquals(-1,pixels[13])
        }
    }
    @Test fun `empty image has no visible bounds`() {
        assertNull(visibleProductBounds(IntArray(16),4,4))
    }
    @Test fun `opaque white packaging counts as visible product`() {
        assertEquals(ProductBounds(0,0,4,4),visibleProductBounds(IntArray(16){-1},4,4))
    }
}
