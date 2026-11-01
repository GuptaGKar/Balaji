package com.balaji.provision.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.balaji.provision.BuildConfig
import com.balaji.provision.data.network.DeliveryFeeRequest
import com.balaji.provision.data.network.SupabaseApi
import com.balaji.provision.data.repository.AccountRepository
import com.balaji.provision.data.repository.AccountResult
import com.balaji.provision.domain.model.formatPaise
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject

data class HomeBanner(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String
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
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val api: SupabaseApi,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        loadStoreInfo()
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
                _state.value = HomeUiState(
                    isLoading = false,
                    storeOpen = settings.store_open,
                    contactPhone = settings.contact_phone,
                    estimatedWindow = settings.estimated_window_text,
                    hours = parseHours(weeklyHours),
                    banners = parseBanners(weeklyHours),
                    deliveryText = deliveryLine,
                    onlinePaymentEnabled = settings.online_payment_enabled
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
            HomeBanner(
                id = (obj["id"] as? JsonPrimitive)?.contentOrNull() ?: imageUrl,
                title = title,
                subtitle = (obj["subtitle"] as? JsonPrimitive)?.contentOrNull().orEmpty(),
                imageUrl = imageUrl
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
