package com.balaji.provision.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.balaji.provision.BuildConfig
import com.balaji.provision.data.auth.AuthRepository
import com.balaji.provision.data.network.DeliveryFeeRequest
import com.balaji.provision.data.network.SupabaseApi
import com.balaji.provision.data.repository.AccountRepository
import com.balaji.provision.data.repository.AccountResult
import com.balaji.provision.data.repository.StorefrontRepository
import com.balaji.provision.data.repository.StorefrontSection
import com.balaji.provision.domain.model.formatPaise
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject

data class HomeBanner(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String,
    /** "category" or "product" when the manager set a destination; otherwise null. */
    val destinationType: String? = null,
    val destinationId: String? = null,
    /**
     * false = show the uploaded image as-is, with no gradient, title, subtitle or
     * button on top. Use it for posters that already contain their own text.
     */
    val overlay: Boolean = true
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val storeOpen: Boolean = false,
    val contactPhone: String? = null,
    val estimatedWindow: String? = null,
    val hours: List<String> = emptyList(),
    val banners: List<HomeBanner> = emptyList(),
    val deliveryText: String? = null,
    val onlinePaymentEnabled: Boolean = false,
    /**
     * Manager-composed sections for this page. Empty means nothing was composed, or the store
     * has not been migrated, or the network failed - all three of which must show today's
     * built-in layout rather than a blank screen.
     */
    val sections: List<StorefrontSection> = emptyList(),
    /**
     * Product ids from this customer's own recent orders, most recently ordered first.
     * Empty while signed out, while the customer has never ordered, or if the read fails - the
     * Home screen then shows no "Buy Again" shelf rather than one that would say the same thing
     * for everybody.
     */
    val orderedProductIds: List<String> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val api: SupabaseApi,
    private val accountRepository: AccountRepository,
    private val storefrontRepository: StorefrontRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        loadStoreInfo()
        loadStorefront()
        // Re-read whenever the signed-in customer changes, not once per process: a sign-in on a
        // cold start has to populate the shelf too, and a different account must never inherit
        // the previous one's history. distinctUntilChanged keeps token refreshes from refetching.
        viewModelScope.launch {
            authRepository.session
                .map { session -> session?.userId }
                .distinctUntilChanged()
                .collect { userId ->
                    if (userId != null) {
                        loadOrderedProductIds(userId)
                    } else {
                        _state.value = _state.value.copy(orderedProductIds = emptyList())
                    }
                }
        }
    }

    /**
     * Reads the product ids the signed-in customer has actually ordered, newest first, so Home
     * can offer a "Buy Again" shelf of their own history. RLS scopes `orders` to the caller, so
     * this returns only this customer's lines; a signed-out or expired session leaves the shelf
     * off the screen instead of showing a shelf of guesses.
     *
     * Removed lines (cancelled before billing) are skipped because they were never bought, and
     * a line whose product has since left the catalogue cannot be added back from Home - the
     * screen only keeps ids it can match to a live product.
     */
    private fun loadOrderedProductIds(userId: String) {
        viewModelScope.launch {
            // authorizedToken() can refresh an expired session, so it is called in here rather
            // than on the caller's thread.
            val token = authRepository.authorizedToken() ?: return@launch
            runCatching {
                val response = api.getOrders(
                    apiKey = BuildConfig.SUPABASE_ANON_KEY,
                    auth = "Bearer $token",
                    customerId = "eq.$userId"
                )
                val orders = response.body()
                if (!response.isSuccessful || orders == null) return@runCatching
                val seen = LinkedHashSet<String>()
                orders.forEach { order ->
                    order.order_items.orEmpty().forEach { item ->
                        val id = item.product_id ?: return@forEach
                        if (item.removed_at.isNullOrBlank()) seen.add(id)
                    }
                }
                _state.value = _state.value.copy(orderedProductIds = seen.toList())
            }
        }
    }

    /**
     * Composed content is ids only, so nothing about price or stock is fetched here - the
     * catalogue already loaded elsewhere supplies both.
     */
    private fun loadStorefront(force: Boolean = false) {
        viewModelScope.launch {
            val sections = storefrontRepository.sectionsFor(page = "home", force = force)
            _state.value = _state.value.copy(sections = sections)
        }
    }

    /**
     * Called when Home is re-entered. The repository decides whether that is actually a refresh
     * based on how long ago it last fetched, so coming back from a product does not re-request.
     */
    fun refreshStorefront() {
        loadStorefront(force = false)
        // Home is re-entered on every tab switch; an order placed since the last visit belongs
        // on the "Buy Again" shelf the next time the customer sees it.
        authRepository.session.value?.userId?.let { loadOrderedProductIds(it) }
    }

    fun loadStoreInfo() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val anonKey = BuildConfig.SUPABASE_ANON_KEY
                val response = api.getStoreSettings(apiKey = anonKey, auth = "Bearer $anonKey")
                val settings = response.body()?.firstOrNull()
                if (!response.isSuccessful || settings == null) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Store information could not be loaded."
                    )
                    return@launch
                }

                val weeklyHours = settings.weekly_hours
                // The customer's own PIN rule wins, so the home strip cannot disagree
                // with the fee the cart and checkout actually charge.
                val deliveryLine = perPinDeliveryText()
                    ?: deliveryText(
                        feePaise = settings.delivery_fee_paise,
                        thresholdPaise = settings.delivery_charge_threshold_paise,
                        freeThresholdPaise = settings.free_delivery_threshold_paise
                    )
                // copy(), never a fresh HomeUiState(): this load runs alongside the storefront
                // read in init(), and a new state object would reset `sections` to its default
                // empty list. The settings path makes more round trips, so it usually lands last
                // - which is why a published section never appeared on screen even though the
                // server was returning it. The error branch below already copied; this one
                // silently did not.
                _state.value = _state.value.copy(
                    isLoading = false,
                    storeOpen = settings.store_open,
                    contactPhone = settings.contact_phone,
                    estimatedWindow = settings.estimated_window_text,
                    hours = parseHours(weeklyHours),
                    banners = parseBanners(weeklyHours),
                    deliveryText = deliveryLine,
                    onlinePaymentEnabled = settings.online_payment_enabled,
                    error = null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Store information could not be loaded. Check your connection."
                )
            }
        }
    }

    private fun parseHours(weeklyHours: JsonObject?): List<String> {
        if (weeklyHours == null) return emptyList()
        val labels = listOf(
            "mon" to "Mon", "tue" to "Tue", "wed" to "Wed", "thu" to "Thu",
            "fri" to "Fri", "sat" to "Sat", "sun" to "Sun"
        )
        return labels.mapNotNull { (key, label) ->
            when (val value = weeklyHours[key]) {
                is JsonArray -> {
                    val open = (value.getOrNull(0) as? JsonPrimitive)?.contentOrNull()
                    val close = (value.getOrNull(1) as? JsonPrimitive)?.contentOrNull()
                    if (open != null && close != null) "$label: $open - $close" else null
                }
                is JsonObject -> {
                    val open = (value["open"] as? JsonPrimitive)?.contentOrNull()
                    val close = (value["close"] as? JsonPrimitive)?.contentOrNull()
                    if (open != null && close != null) "$label: $open - $close" else null
                }
                else -> null
            }
        }
    }

    private fun parseBanners(weeklyHours: JsonObject?): List<HomeBanner> {
        val array = weeklyHours?.get("promotional_banners") as? JsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val title = (obj["title"] as? JsonPrimitive)?.contentOrNull()?.trim().orEmpty()
            val imageUrl = ((obj["image_url"] ?: obj["imageUrl"]) as? JsonPrimitive)?.contentOrNull()?.trim().orEmpty()
            if (title.isBlank() || !(imageUrl.startsWith("https://") || imageUrl.startsWith("http://"))) {
                return@mapNotNull null
            }
            // Optional destination, either {"destination": {"type", "id"}} or the flat
            // destination_type / destination_id keys. Anything else is ignored so a
            // malformed banner still shows and simply opens the catalogue.
            val destination = obj["destination"] as? JsonObject
            val destinationType = ((destination?.get("type") ?: obj["destination_type"]) as? JsonPrimitive)
                ?.contentOrNull()?.trim()?.lowercase()
                ?.takeIf { it == "category" || it == "product" }
            val destinationId = ((destination?.get("id") ?: obj["destination_id"]) as? JsonPrimitive)
                ?.contentOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            val hasDestination = destinationType != null && destinationId != null
            HomeBanner(
                id = (obj["id"] as? JsonPrimitive)?.contentOrNull() ?: imageUrl,
                title = title,
                subtitle = (obj["subtitle"] as? JsonPrimitive)?.contentOrNull().orEmpty(),
                imageUrl = imageUrl,
                destinationType = if (hasDestination) destinationType else null,
                destinationId = if (hasDestination) destinationId else null,
                overlay = (obj["overlay"] as? JsonPrimitive)?.contentOrNull()
                    ?.trim()?.lowercase()?.toBooleanStrictOrNull() ?: true
            )
        }
    }

    /**
     * Delivery wording for the signed-in customer's default PIN, or null when they
     * have no saved address, are signed out, or the lookup fails — in which case the
     * store-wide rule is the honest fallback.
     */
    private suspend fun perPinDeliveryText(): String? {
        val addresses = (accountRepository.loadAddresses() as? AccountResult.Success)?.value
            ?: return null
        val pin = (addresses.firstOrNull { it.isDefault } ?: addresses.firstOrNull())
            ?.postalCode?.takeIf { it.isNotBlank() }
            ?: return null
        return try {
            val anonKey = BuildConfig.SUPABASE_ANON_KEY
            val fee = api.computeDeliveryFeeForPin(
                apiKey = anonKey,
                auth = "Bearer $anonKey",
                body = DeliveryFeeRequest(p_eligible_subtotal = 0L, p_pin_code = pin)
            ).body()?.firstOrNull() ?: return null
            when {
                fee.fee_paise <= 0L -> "Free delivery"
                fee.amount_to_free_delivery > 0L ->
                    "${formatPaise(fee.fee_paise)} delivery; free at or above " +
                        formatPaise(fee.amount_to_free_delivery)
                else -> "${formatPaise(fee.fee_paise)} delivery"
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun deliveryText(feePaise: Long?, thresholdPaise: Long?, freeThresholdPaise: Long?): String? {
        if (feePaise == null || thresholdPaise == null || thresholdPaise <= 0L) return null
        return if (freeThresholdPaise != null && freeThresholdPaise > 0L) {
            "Free delivery on orders of ${formatPaise(freeThresholdPaise)} or more"
        } else {
            "${formatPaise(feePaise)} delivery below ${formatPaise(thresholdPaise)}; free at or above"
        }
    }

    private fun JsonPrimitive.contentOrNull(): String? =
        if (isString) content else content.takeIf { it != "null" }
}
