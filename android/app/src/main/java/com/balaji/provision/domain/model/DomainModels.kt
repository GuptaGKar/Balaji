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
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CartItem) return false
        return productId == other.productId
    }
    
    override fun hashCode(): Int = productId.hashCode()
}

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
    val amountToFreeDelivery: Long = 0
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
    val priceBasisQty: Long,
    val unitLabel: String,
    val packSizeLabel: String?,
    val stepQty: Long,
    val minQty: Long,
    val maxQty: Long?,
    val stockQty: Long,
    val lowStockThreshold: Long,
    val active: Boolean,
    val available: Boolean
) {
    val displayPrice: String
        get() = formatPrice(pricePaise, priceBasisQty, quantityType)
    
    val displayStep: String
        get() = when (quantityType) {
            QuantityType.PACKED -> "1 ${unitLabel}"
            QuantityType.WEIGHTED -> "${stepQty} g"
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
    val active: Boolean
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
    val rupees = paise / 100.0
    return "₹${String.format(java.util.Locale.US, "%,.2f", rupees)}"
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