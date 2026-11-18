package com.balaji.provision.ui.manager

import com.balaji.provision.domain.model.LowStockProduct
import com.balaji.provision.domain.model.Product

internal fun matchesStockSearch(name: String, sku: String, query: String): Boolean {
    val trimmed = query.trim()
    return name.contains(trimmed, ignoreCase = true) || sku.contains(trimmed, ignoreCase = true)
}

internal fun Product.toStockAdjustmentProduct() = LowStockProduct(
    id = id,
    name = name,
    sku = sku,
    stockQty = stockQty,
    threshold = lowStockThreshold,
    quantityType = quantityType.name.lowercase(),
    unitLabel = unitLabel,
    priceBasisQty = priceBasisQty,
    pricePaise = pricePaise,
    mrpPaise = mrpPaise
)

/** Input assistance only; adjust_stock validates and applies the delta under a row lock. */
internal fun stockAdjustmentDelta(amount: String, adding: Boolean, currentStock: Long): Long? {
    val text = amount.trim()
    if (text.isEmpty() || text.any { it !in '0'..'9' } || currentStock < 0) return null
    val quantity = text.toLongOrNull()?.takeIf { it > 0 } ?: return null
    return if (adding) {
        quantity.takeIf { it <= Long.MAX_VALUE - currentStock }
    } else {
        quantity.takeIf { it <= currentStock }?.let { -it }
    }
}
