package com.balaji.provision.domain.model

data class CustomerProfile(
    val displayName: String?,
    val phone: String?,
    val role: String,
    val isActive: Boolean = true,
    val mustChangePassword: Boolean = false
)

data class SavedAddress(
    val id: String,
    val label: String,
    val recipientName: String,
    val phone: String,
    val addressLines: String,
    val landmark: String?,
    val area: String?,
    val city: String,
    val state: String,
    val postalCode: String,
    val isDefault: Boolean,
    val lat: Double? = null,
    val lng: Double? = null
) {
    fun fullAddress(): String {
        val parts = listOfNotNull(
            addressLines,
            landmark,
            area,
            city,
            state
        ).map(String::trim).filter(String::isNotEmpty)
        return parts.joinToString(", ") + " - $postalCode"
    }

    fun toDraft(): AddressDraft = AddressDraft(
        label = label.ifBlank { "Home" },
        recipientName = recipientName,
        phone = phone,
        addressLines = addressLines,
        landmark = landmark.orEmpty(),
        area = area.orEmpty(),
        city = city,
        state = state,
        postalCode = postalCode,
        isDefault = isDefault,
        lat = lat,
        lng = lng
    )
}

data class AddressDraft(
    val label: String = "Home",
    val recipientName: String = "",
    val phone: String = "",
    val addressLines: String = "",
    val landmark: String = "",
    val area: String = "",
    val city: String = "",
    val state: String = "",
    val postalCode: String = "",
    val isDefault: Boolean = false,
    val lat: Double? = null,
    val lng: Double? = null
) {
    fun validate(): String? = when {
        recipientName.isBlank() -> "Enter the recipient name."
        normalizedPhone() == null -> "Enter a valid 10-digit mobile number."
        addressLines.isBlank() -> "Enter the flat, house number and street."
        city.isBlank() -> "Enter the city."
        state.isBlank() -> "Enter the state."
        !postalCode.trim().matches(Regex("^\\d{6}$")) -> "Enter a valid 6-digit PIN code."
        (lat == null) != (lng == null) -> "Location needs both latitude and longitude."
        lat != null && (lat < -90 || lat > 90 || lng!! < -180 || lng!! > 180) ->
            "Invalid location coordinates."
        else -> null
    }

    fun normalizedPhone(): String? {
        val digits = phone.filter(Char::isDigit)
        val local = when {
            digits.length == 10 -> digits
            digits.length == 11 && digits.startsWith("0") -> digits.drop(1)
            digits.length == 12 && digits.startsWith("91") -> digits.drop(2)
            else -> return null
        }
        return local.takeIf { it.firstOrNull()?.let { first -> first in '6'..'9' } == true }
    }
}
