package com.balaji.provision.data.location

import com.balaji.provision.domain.model.AddressDraft

/**
 * One completion offered by the address form, from the store's own data: either an active
 * service area (`kind` is `pincode` or `neighbourhood`) or a saved address belonging to the
 * signed-in customer.
 *
 * Nothing here is a guess about the rest of the world. The server only answers with rows this
 * project already holds, so a completion is a promise that the store serves that area - unlike a
 * third-party geocoder, which would happily suggest an address it cannot deliver to.
 */
data class AreaSuggestion(
    val type: SuggestionType,
    val id: String,
    /** What to show on the chip: the area name, or the address label. */
    val label: String,
    /** Second line, e.g. "PIN 560036" or the city. */
    val sublabel: String? = null,
    val kind: String? = null,
    val value: String? = null,
    /** Zone centroid (areas) or the saved pin (addresses). Null means "no location known". */
    val point: GeoPoint? = null,
    val recipientName: String? = null,
    val phone: String? = null,
    val addressLines: String? = null,
    val landmark: String? = null,
    val area: String? = null,
    val city: String? = null,
    val state: String? = null,
    val postalCode: String? = null
)

enum class SuggestionType { AREA, SAVED_ADDRESS }

/**
 * Below two characters the answer is noise, and a query this long is a paste rather than a
 * search. The server applies the same bounds; agreeing here is what stops a request per
 * keystroke, not a security measure.
 */
const val MIN_QUERY_LENGTH = 2
const val MAX_QUERY_LENGTH = 64

fun suggestionQueryIsSearchable(raw: String): Boolean =
    raw.trim().length in MIN_QUERY_LENGTH..MAX_QUERY_LENGTH

/** Shortest text a chip can show without becoming two identical lines. */
fun AreaSuggestion.chipLabel(): String = sublabel?.takeIf { it.isNotBlank() }?.let { "$label · $it" } ?: label

/**
 * Folds a tapped completion into the draft being edited.
 *
 * Deliberately narrow: `service_areas` stores no city or state, so an area chip fills only the
 * area, the PIN when the area *is* a PIN, and the map position. Inventing a city to make the
 * form look fuller would be worse than leaving the field for the customer, and that limit is
 * stated in the UI copy rather than hidden.
 */
fun AreaSuggestion.patchOnto(draft: AddressDraft): AddressDraft = when (type) {
    SuggestionType.AREA -> draft.copy(
        area = label.orKeep(draft.area),
        postalCode = if (kind == PINCODE_KIND) value.orKeep(draft.postalCode) else draft.postalCode,
        lat = point?.latitude ?: draft.lat,
        lng = point?.longitude ?: draft.lng
    )

    SuggestionType.SAVED_ADDRESS -> draft.copy(
        recipientName = recipientName.orKeep(draft.recipientName),
        phone = phone.orKeep(draft.phone),
        addressLines = addressLines.orKeep(draft.addressLines),
        landmark = landmark.orKeep(draft.landmark),
        area = area.orKeep(draft.area),
        city = city.orKeep(draft.city),
        state = state.orKeep(draft.state),
        postalCode = postalCode.orKeep(draft.postalCode),
        // A saved pin is the customer's own chosen spot, so it is worth more than the area
        // centre; an address without coordinates must not clear a pin already dropped.
        lat = point?.latitude ?: draft.lat,
        lng = point?.longitude ?: draft.lng
    )
}

private const val PINCODE_KIND = "pincode"

/** Blank and absent are the same thing here: neither should wipe what the customer typed. */
private fun String?.orKeep(current: String): String =
    this?.takeIf { it.isNotBlank() } ?: current
