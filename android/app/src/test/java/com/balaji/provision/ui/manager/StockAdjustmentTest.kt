package com.balaji.provision.ui.manager

import com.balaji.provision.domain.model.LowStockProduct
import com.balaji.provision.domain.model.Product
import com.balaji.provision.domain.model.QuantityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StockAdjustmentTest {
    @Test
    fun `search filters both lists by trimmed name or SKU ignoring case`() {
        val rice = product("rice", "Basmati Rice", "RICE-1")
        val oil = product("oil", "Cooking Oil", "OIL-1")
        val state = ManagerUiState(
            products = listOf(rice, oil),
            lowStock = listOf(rice.toStockAdjustmentProduct(), oil.toStockAdjustmentProduct()),
            stockSearchQuery = "  rIcE-1  "
        )
        assertEquals(listOf("rice"), state.filteredStockProducts.map { it.id })
        assertEquals(listOf("rice"), state.filteredLowStock.map { it.id })
        assertEquals(listOf("oil"), state.copy(stockSearchQuery = "cooking").filteredStockProducts.map { it.id })
        assertEquals(2, state.copy(stockSearchQuery = " ").filteredLowStock.size)
        assertEquals(emptyList<LowStockProduct>(), state.copy(stockSearchQuery = "absent").filteredLowStock)
    }

    @Test
    fun `normal catalogue rows preserve weighted stock and price units for editing`() {
        val row = product("rice", "Rice", "RICE").copy(
            quantityType = QuantityType.WEIGHTED, stockQty = 12500,
            priceBasisQty = 1000, unitLabel = "g", mrpPaise = 15000
        ).toStockAdjustmentProduct()
        assertEquals("weighted", row.quantityType)
        assertEquals("12 kg 500 g", row.quantityLabel)
        assertEquals("kg", row.priceUnitLabel)
        assertEquals(15000L, row.mrpPaise)
    }

    @Test
    fun `adjustments accept exact whole units and allow removing all stock`() {
        assertEquals(1250L, stockAdjustmentDelta(" 1250 ", true, 0))
        assertEquals(-12L, stockAdjustmentDelta("12", false, 12))
        assertEquals(1L, stockAdjustmentDelta("1", true, Long.MAX_VALUE - 1))
    }

    @Test
    fun `invalid fractional and overflowing adjustments cannot be submitted`() {
        listOf("", "0", "-1", "1.5", "1e2", "1,000", "12a", "9223372036854775808").forEach {
            assertNull(it, stockAdjustmentDelta(it, true, 20))
        }
        assertNull(stockAdjustmentDelta("21", false, 20))
        assertNull(stockAdjustmentDelta("1", true, Long.MAX_VALUE))
    }

    private fun product(id: String, name: String, sku: String) = Product(
        id = id, sku = sku, name = name, description = null, categoryId = "test",
        imagePath = null, quantityType = QuantityType.PACKED, pricePaise = 12000,
        priceBasisQty = 1, unitLabel = "pack", packSizeLabel = null, stepQty = 1,
        minQty = 1, maxQty = null, stockQty = 20, lowStockThreshold = 2,
        active = true, available = true
    )
}
