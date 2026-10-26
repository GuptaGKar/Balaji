package com.balaji.provision.domain.model

data class ManagerOrder(
    val id: String,
    val orderNumber: String,
    val status: String,
    val grandTotalPaise: Long,
    val createdAt: String,
    val paymentMethod: String?,
    val paymentStatus: String?,
    val codCollectedPaise: Long?,
    val recipientName: String,
    val phone: String,
    val addressLines: String,
    val customerNote: String?,
    val itemSubtotalPaise: Long,
    val deliveryFeePaise: Long,
    val taxPaise: Long
) {
    val statusLabel: String
        get() = status.replace('_', ' ').replaceFirstChar { it.uppercase() }

    val isCod: Boolean
        get() = paymentMethod == "cod"

    val canAssignRider: Boolean
        get() = status == "ready_for_dispatch" || status == "out_for_delivery"

    val actions: List<ManagerOrderAction>
        get() = when (status) {
            "pending_review" -> listOf(
                ManagerOrderAction("accepted", "Accept order"),
                ManagerOrderAction("rejected", "Reject order", requiresReason = true, destructive = true)
            )
            "accepted" -> listOf(
                ManagerOrderAction("preparing", "Start preparing"),
                ManagerOrderAction("cancelled", "Cancel order", requiresReason = true, destructive = true)
            )
            "preparing" -> listOf(
                ManagerOrderAction("ready_for_dispatch", "Ready for dispatch"),
                ManagerOrderAction("cancelled", "Cancel order", requiresReason = true, destructive = true)
            )
            "ready_for_dispatch" -> listOf(
                ManagerOrderAction("out_for_delivery", "Dispatch delivery"),
                ManagerOrderAction("cancelled", "Cancel order", requiresReason = true, destructive = true)
            )
            "out_for_delivery" -> listOf(
                ManagerOrderAction("delivered", "Mark delivered"),
                ManagerOrderAction("delivery_failed", "Delivery failed", requiresReason = true, destructive = true)
            )
            else -> emptyList()
        }
}

data class ManagerOrderAction(
    val toStatus: String,
    val label: String,
    val requiresReason: Boolean = false,
    val destructive: Boolean = false
)

data class ManagerOrderLine(
    val id: String,
    val name: String,
    val quantityLabel: String,
    val unitPricePaise: Long,
    val lineTotalPaise: Long,
    val removed: Boolean,
    val removedReason: String?
)

data class LowStockProduct(
    val id: String,
    val name: String,
    val sku: String,
    val stockQty: Long,
    val threshold: Long,
    val quantityType: String,
    val unitLabel: String,
    val pricePaise: Long = 0L,
    val mrpPaise: Long? = null
) {
    val quantityLabel: String
        get() = formatBaseQuantity(quantityType, stockQty, unitLabel)

    val hasDiscount: Boolean
        get() = mrpPaise != null && mrpPaise > pricePaise
}

data class RiderOption(
    val id: String,
    val name: String,
    val phone: String?
)

fun formatBaseQuantity(quantityType: String?, qtyBase: Long, unitLabel: String?): String {
    if (quantityType == "weighted") {
        val kg = qtyBase / 1000
        val grams = qtyBase % 1000
        return when {
            kg > 0 && grams > 0 -> "$kg kg $grams g"
            kg > 0 -> "$kg kg"
            else -> "$grams g"
        }
    }
    return "$qtyBase x ${unitLabel?.takeIf { it.isNotBlank() } ?: "pack"}"
}
