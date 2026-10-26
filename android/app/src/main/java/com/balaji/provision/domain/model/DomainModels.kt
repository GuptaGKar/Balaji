package com.balaji.provision.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class Cart(
    val items: List<CartItem> = emptyList()
) {
    companion object {
        fun empty(): Cart = Cart(emptyList())
    }
    
    val itemCount: Int
        get() = items.sumOf { it.qtyBase.toInt() }
    
    val isEmpty: Boolean
        get() = items.isEmpty()
}

@Immutable
@Serializable
data class CartItem(
    val productId: String,
    val qtyBase: Long
)

@Immutable
@Serializable
data class QuoteBreakdown(
    val lines: List<Line> = emptyList(),
    val itemSubtotalPaise: Long = 0,
    val discountPaise: Long = 0,
    val deliveryFeePaise: Long = 0,
    val feeRuleNote: String = "",
    val taxPaise: Long = 0,
    val grandTotalPaise: Long = 0,
    val belowChargeThreshold: Boolean = false,
    val amountToFreeDelivery: Long = 0,
    val couponCode: String? = null,
    val couponApplied: Boolean = false,
    val couponError: String? = null
) {

    @Immutable
    @Serializable
    data class Line(
        val productId: String,
        val name: String,
        val qtyBase: Long,
        val displayQty: String,
        val unitPricePaise: Long,
        val lineTotalPaise: Long,
        val available: Boolean,
        val error: String?
    )
}

@Immutable
@Serializable
data class Product(
    val id: String,
    val sku: String,
    val name: String,
    val description: String?,
    val categoryId: String,
    val imagePath: String?,
    val quantityType: QuantityType,
    val pricePaise: Long,
    val mrpPaise: Long? = null,
    val priceBasisQty: Long,
    val unitLabel: String,
    val packSizeLabel: String?,
    val stepQty: Long,
    val minQty: Long,
    val maxQty: Long?,
    val stockQty: Long,
    val lowStockThreshold: Long,
    val active: Boolean,
    val available: Boolean,
    val variantGroupId: String? = null,
    val variantLabel: String? = null
) {
    val displayPrice: String
        get() = formatPrice(pricePaise, priceBasisQty, quantityType)

    val hasDiscount: Boolean
        get() = mrpPaise != null && mrpPaise > pricePaise

    val displayMrp: String?
        get() = if (hasDiscount) formatPrice(mrpPaise!!, priceBasisQty, quantityType) else null

    val discountPercent: Int
        get() = if (hasDiscount && mrpPaise!! > 0) {
            (((mrpPaise - pricePaise).toDouble() / mrpPaise) * 100).toInt()
        } else 0

    val displayStep: String
        get() = when (quantityType) {
            QuantityType.PACKED -> "1 ${unitLabel}"
            QuantityType.WEIGHTED -> "${stepQty} g"
        }

    /**
     * Short pack-size text used by cards, the cart and the details screen.
     * Prefer the explicit variant label; otherwise fall back to the pack size
     * and finally the plain unit label. Weighted goods keep their basis weight.
     */
    val packLabel: String
        get() = variantLabel?.takeIf { it.isNotBlank() }
            ?: packSizeLabel?.takeIf { it.isNotBlank() }
            ?: when (quantityType) {
                QuantityType.WEIGHTED -> "$priceBasisQty g"
                QuantityType.PACKED -> unitLabel
            }

    val minOrderQty: Long
        get() = minQty
    
    val maxOrderQty: Long
        get() = maxQty ?: stockQty
    
    val isLowStock: Boolean
        get() = stockQty <= lowStockThreshold && stockQty > 0
    
    val isInStock: Boolean
        get() = stockQty >= minQty
}

/**
 * Case-insensitive match used by catalogue search. Covers the pack label and
 * SKU so a query like "1 l" or "family pack" finds the right variant.
 */
fun Product.variantsMatch(query: String): Boolean {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return false
    return packLabel.lowercase().contains(needle) ||
        sku.lowercase().contains(needle) ||
        (variantLabel?.lowercase()?.contains(needle) == true)
}

enum class QuantityType {
    WEIGHTED, PACKED
}

@Immutable
@Serializable
data class Category(
    val id: String,
    val name: String,
    val slug: String,
    val sortOrder: Int,
    val active: Boolean,
    val imagePath: String? = null
)

@Immutable
@Serializable
data class Order(
    val id: String,
    val publicOrderNumber: String,
    val customerId: String,
    val status: OrderStatus,
    val paymentMethod: PaymentMethod,
    val paymentStatus: PaymentStatus,
    val itemSubtotalPaise: Long,
    val discountPaise: Long,
    val deliveryFeePaise: Long,
    val taxPaise: Long,
    val grandTotalPaise: Long,
    val items: List<OrderItem> = emptyList(),
    val createdAt: String,
    val acceptedAt: String?,
    val deliveredAt: String?
) {
    val formattedTotal: String
        get() = formatPaise(grandTotalPaise)
}

enum class OrderStatus {
    PENDING_REVIEW, ACCEPTED, PREPARING, READY_FOR_DISPATCH,
    OUT_FOR_DELIVERY, DELIVERED, REJECTED, CANCELLED, DELIVERY_FAILED
}

enum class PaymentMethod {
    COD, ONLINE
}

enum class PaymentStatus {
    UNPAID, PENDING, PAID, FAILED, REFUND_PENDING, PARTIALLY_REFUNDED, REFUNDED
}

@Immutable
@Serializable
data class OrderItem(
    val id: String,
    val productId: String?,
    val name: String,
    val qtyBase: Long,
    val unitLabel: String,
    val quantityType: QuantityType,
    val unitPricePaise: Long,
    val lineTotalPaise: Long
)

@Immutable
@Serializable
data class Address(
    val id: String,
    val recipientName: String,
    val phone: String,
    val addressLines: String,
    val landmark: String?,
    val area: String?,
    val city: String?,
    val state: String?,
    val postalCode: String,
    val isDefault: Boolean
) {
    val fullAddress: String
        get() = "$addressLines${landmark?.let { ", $it" } ?: ""}${area?.let { ", $it" } ?: ""}, $city - $postalCode"
}

@Immutable
@Serializable
data class UserProfile(
    val id: String,
    val role: UserRole,
    val displayName: String,
    val phone: String,
    val loginId: String?,
    val isActive: Boolean,
    val mustChangePassword: Boolean
)

enum class UserRole {
    CUSTOMER, STAFF, MANAGER, DELIVERY
}

fun formatPaise(paise: Long): String {
    val negative = paise < 0
    val absolute = kotlin.math.abs(paise)
    val rupees = absolute / 100
    val fraction = absolute % 100
    val digits = rupees.toString()
    val grouped = if (digits.length <= 3) {
        digits
    } else {
        val head = digits.dropLast(3)
        val tail = digits.takeLast(3)
        head.reversed().chunked(2).joinToString(",").reversed() + "," + tail
    }
    val sign = if (negative) "-" else ""
    return "$sign₹$grouped.${fraction.toString().padStart(2, '0')}"
}

fun formatWeightGrams(grams: Long): String {
    if (grams < 1000) return "$grams g"
    val kg = grams / 1000
    val remainder = grams % 1000
    if (remainder == 0L) return "$kg kg"
    val fraction = remainder.toString().padStart(3, '0').trimEnd('0')
    return "$kg.$fraction kg"
}

fun lineTotalPaise(pricePaise: Long, priceBasisQty: Long, qtyBase: Long): Long {
    if (priceBasisQty <= 0) return 0
    return (pricePaise * qtyBase + priceBasisQty / 2) / priceBasisQty
}

fun formatPrice(pricePaise: Long, priceBasisQty: Long, quantityType: QuantityType): String {
    return when (quantityType) {
        QuantityType.PACKED -> formatPaise(pricePaise) + " / pack"
        QuantityType.WEIGHTED -> {
            val perUnit = when (priceBasisQty) {
                1000L -> "kg"
                100L -> "100g"
                else -> "g"
            }
            formatPaise(pricePaise) + " / $perUnit"
        }
    }
}

/**
 * A base product and its pack-size variants.
 *
 * The catalogue is a flat `products` table where each pack size is its own row
 * (own SKU, price, stock, availability). Rows that share a `variantGroupId` are
 * pack sizes of one base product; the client groups them so one card can offer
 * a pack selector. `defaultVariant` is the variant shown on the listing card and
 * pre-selected when the details screen opens.
 */
@Immutable
@Serializable
data class ProductVariantGroup(
    val groupId: String,
    val baseName: String,
    val description: String?,
    val imagePath: String?,
    val categoryId: String,
    val variants: List<Product>,
    val defaultVariant: Product
) {
    val hasMultipleVariants: Boolean
        get() = variants.size > 1

    /** Compact pack list for cards, e.g. "500 ml · 1 L · 2 L". */
    val packSummary: String
        get() = variants.joinToString(" · ") { it.packLabel }
}

/**
 * Groups a flat catalogue into [ProductVariantGroup]s.
 *
 * Products with no `variantGroupId` become single-variant groups keyed by their
 * own id, so the rest of the app can treat every listing uniformly. The default
 * variant prefers an in-stock, active, available row and otherwise falls back to
 * the cheapest option so a card never defaults to an unbuyable pack.
 */
fun List<Product>.toVariantGroups(): List<ProductVariantGroup> {
    val active = filter { it.active }
    val grouped = active.groupBy { it.variantGroupId ?: "solo:${it.id}" }
    return grouped.mapNotNull { (key, members) ->
        val ordered = members.sortedWith(
            compareByDescending<Product> { it.isInStock }
                .thenBy { it.pricePaise }
        )
        val default = ordered.firstOrNull() ?: return@mapNotNull null
        ProductVariantGroup(
            groupId = key,
            baseName = commonBaseName(members),
            description = members.firstOrNull { !it.description.isNullOrBlank() }?.description,
            imagePath = members.firstOrNull { !it.imagePath.isNullOrBlank() }?.imagePath
                ?: default.imagePath,
            categoryId = default.categoryId,
            variants = ordered,
            defaultVariant = default
        )
    }.sortedBy { it.baseName.lowercase() }
}

/**
 * Derives the shared display name for a group. Pack sizes are usually named
 * "Amul Milk 500ml" / "Amul Milk 1L"; when one name is a prefix of the others
 * the shortest one is the base name, otherwise the default variant's name is
 * used so the card always shows something real.
 */
private fun commonBaseName(members: List<Product>): String {
    val names = members.map { it.name.trim() }.filter { it.isNotBlank() }
    if (names.isEmpty()) return members.firstOrNull()?.name.orEmpty()
    val sorted = names.sortedBy { it.length }
    val shortest = sorted.first()
    if (sorted.all { it.startsWith(shortest, ignoreCase = true) }) return shortest
    return members.minByOrNull { it.pricePaise }?.name ?: shortest
}