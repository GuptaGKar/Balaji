package com.balaji.provision.domain.model

data class RiderAddress(
    val recipientName: String,
    val phone: String,
    val addressLines: String,
    val landmark: String?,
    val area: String?,
    val city: String?,
    val state: String?,
    val postalCode: String?,
    val lat: Double?,
    val lng: Double?
) {
    val displayAddress: String
        get() = listOfNotNull(
            addressLines.takeIf { it.isNotBlank() },
            landmark?.takeIf { it.isNotBlank() },
            area?.takeIf { it.isNotBlank() },
            city?.takeIf { it.isNotBlank() },
            state?.takeIf { it.isNotBlank() },
            postalCode?.takeIf { it.isNotBlank() }
        ).joinToString(", ")

    val hasCoordinates: Boolean
        get() = lat != null && lng != null
}

data class RiderOrderItem(
    val name: String,
    val displayQty: String,
    val lineTotalPaise: Long
)

data class RiderOrder(
    val id: String,
    val orderNumber: String,
    val address: RiderAddress?,
    val paymentMethod: String,
    val codCollectedPaise: Long?,
    val grandTotalPaise: Long,
    val status: String,
    val customerNote: String?,
    val createdAt: String,
    val items: List<RiderOrderItem>
) {
    val isCod: Boolean
        get() = paymentMethod == "cod"

    val isActive: Boolean
        get() = status == "ready_for_dispatch" || status == "out_for_delivery"

    val statusLabel: String
        get() = status.replace('_', ' ').replaceFirstChar { it.uppercase() }

    val actions: List<RiderAction>
        get() = when (status) {
            "ready_for_dispatch" -> listOf(RiderAction("out_for_delivery", "Start delivery"))
            "out_for_delivery" -> listOf(
                RiderAction("delivered", "Mark delivered"),
                RiderAction("delivery_failed", "Delivery failed")
            )
            else -> emptyList()
        }
}

data class RiderAction(
    val toStatus: String,
    val label: String
)
