package com.balaji.provision.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.balaji.provision.BuildConfig
import com.balaji.provision.data.auth.AuthRepository
import com.balaji.provision.data.network.AddressRequest
import com.balaji.provision.data.network.CartItemRequest
import com.balaji.provision.data.network.PlaceOrderPayload
import com.balaji.provision.data.network.PlaceOrderRequest
import com.balaji.provision.data.network.PlaceOrderResponse
import com.balaji.provision.data.network.QuoteCartRequest
import com.balaji.provision.data.network.QuoteCartResponse
import com.balaji.provision.data.network.SupabaseApi
import com.balaji.provision.domain.model.Cart
import com.balaji.provision.domain.model.CartItem
import com.balaji.provision.domain.model.QuoteBreakdown
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CartModule {
    
    @Provides
    @Singleton
    fun provideCartRepository(
        api: SupabaseApi,
        authRepository: AuthRepository,
        dataStore: DataStore<Preferences>,
        @Named("cartItemsKey") cartItemsKey: Preferences.Key<String>
    ): CartRepository = CartRepositoryImpl(api, authRepository, dataStore, cartItemsKey)
}

@Singleton
class CartRepositoryImpl @Inject constructor(
    private val api: SupabaseApi,
    private val authRepository: AuthRepository,
    private val dataStore: DataStore<Preferences>,
    @Named("cartItemsKey") private val cartItemsKey: Preferences.Key<String>
) : CartRepository {

    private val cartLock = Any()
    private val inMemoryCartItems = mutableListOf<CartItem>()
    private val json = Json { ignoreUnknownKeys = true }
    private val persistMutex = Mutex()
    private val cartItemsSerializer = ListSerializer(CartItem.serializer())

    private var pendingOrderKey: String? = null
    private var pendingOrderSignature: String? = null

    @Volatile private var pendingCouponCode: String? = null

    private val _cartFlow = MutableStateFlow<Cart>(Cart.empty())
    override val cartFlow: StateFlow<Cart> = _cartFlow.asStateFlow()

    private val _quoteFlow = MutableStateFlow<QuoteBreakdown?>(null)
    override val quoteFlow: StateFlow<QuoteBreakdown?> = _quoteFlow.asStateFlow()

    private val quoteScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var quoteJob: Job? = null
    @Volatile private var quoteVersion = 0L

    @Volatile private var hasLocalMutations = false

    init {
        quoteScope.launch { restorePersistedCart() }
    }

    private suspend fun restorePersistedCart() {
        val restored = try {
            dataStore.data.first()[cartItemsKey]?.let { raw ->
                json.decodeFromString(cartItemsSerializer, raw)
            }.orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
        val applied = synchronized(cartLock) {
            if (hasLocalMutations || inMemoryCartItems.isNotEmpty()) {
                false
            } else {
                inMemoryCartItems.addAll(
                    restored.filter { it.productId.isNotBlank() && it.qtyBase > 0 }
                )
                true
            }
        }
        if (applied && inMemoryCartItems.isNotEmpty()) {
            notifyStateChanged()
            scheduleQuoteRefresh()
        }
    }

    private suspend fun persistCart() {
        val snapshot = synchronized(cartLock) { inMemoryCartItems.toList() }
        try {
            persistMutex.withLock {
                dataStore.edit { prefs ->
                    if (snapshot.isEmpty()) {
                        prefs.remove(cartItemsKey)
                    } else {
                        prefs[cartItemsKey] = json.encodeToString(cartItemsSerializer, snapshot)
                    }
                }
            }
        } catch (_: Exception) {
            // A cache write failure must never break the in-memory cart.
        }
    }

    private fun notifyStateChanged() {
        val snapshot = synchronized(cartLock) { inMemoryCartItems.toList() }
        _cartFlow.value = Cart(items = snapshot)
    }

    private fun scheduleQuoteRefresh(debounceMillis: Long = 180L) {
        val snapshot = synchronized(cartLock) { inMemoryCartItems.toList() }
        quoteVersion += 1
        val version = quoteVersion
        quoteJob?.cancel()

        if (snapshot.isEmpty()) {
            _quoteFlow.value = QuoteBreakdown()
            return
        }

        quoteJob = quoteScope.launch {
            if (debounceMillis > 0) delay(debounceMillis)
            val quote = fetchQuote(snapshot)
            if (version == quoteVersion) {
                _quoteFlow.value = quote
            }
        }
    }

    private suspend fun fetchQuote(items: List<CartItem>): QuoteBreakdown? {
        return try {
            val coupon = pendingCouponCode?.trim()?.takeIf { it.isNotBlank() }
            val request = QuoteCartRequest(
                p_items = items.map { CartItemRequest(it.productId, it.qtyBase) },
                p_coupon_code = coupon
            )
            val anonKey = BuildConfig.SUPABASE_ANON_KEY
            val response = api.quoteCart(apiKey = anonKey, auth = "Bearer $anonKey", body = request)
            if (response.isSuccessful) {
                response.body()?.let { mapToQuoteBreakdown(it) }
            } else {
                val err = response.errorBody()?.string()
                android.util.Log.e("CartRepository", "Quote calculation failed HTTP ${response.code()}: $err")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("CartRepository", "Quote calculation exception", e)
            null
        }
    }

    override fun refreshQuote() {
        scheduleQuoteRefresh(debounceMillis = 0L)
    }

    override suspend fun getCart(): Cart = _cartFlow.value

    override suspend fun addItem(item: CartItem): Cart {
        synchronized(cartLock) {
            hasLocalMutations = true
            val existingIndex = inMemoryCartItems.indexOfFirst { it.productId == item.productId }
            if (existingIndex >= 0) {
                val existing = inMemoryCartItems[existingIndex]
                inMemoryCartItems[existingIndex] = existing.copy(qtyBase = existing.qtyBase + item.qtyBase)
            } else {
                inMemoryCartItems.add(item)
            }
        }
        notifyStateChanged()
        persistCart()
        scheduleQuoteRefresh()
        return _cartFlow.value
    }

    override suspend fun updateQuantity(productId: String, qtyBase: Long): Cart {
        synchronized(cartLock) {
            hasLocalMutations = true
            if (qtyBase <= 0) {
                inMemoryCartItems.removeAll { it.productId == productId }
            } else {
                val index = inMemoryCartItems.indexOfFirst { it.productId == productId }
                if (index >= 0) {
                    inMemoryCartItems[index] = CartItem(productId, qtyBase)
                } else {
                    inMemoryCartItems.add(CartItem(productId, qtyBase))
                }
            }
        }
        notifyStateChanged()
        persistCart()
        scheduleQuoteRefresh()
        return _cartFlow.value
    }

    override suspend fun removeItem(productId: String): Cart {
        synchronized(cartLock) {
            hasLocalMutations = true
            inMemoryCartItems.removeAll { it.productId == productId }
        }
        notifyStateChanged()
        persistCart()
        scheduleQuoteRefresh()
        return _cartFlow.value
    }

    override suspend fun clearCart() {
        synchronized(cartLock) {
            hasLocalMutations = true
            inMemoryCartItems.clear()
        }
        pendingOrderKey = null
        pendingOrderSignature = null
        notifyStateChanged()
        persistCart()
        scheduleQuoteRefresh()
    }

    override suspend fun placeOrderRemote(address: AddressRequest, customerNote: String?): PlaceOrderResult {
        val items = synchronized(cartLock) {
            inMemoryCartItems.map { CartItemRequest(it.productId, it.qtyBase) }
        }
        if (items.isEmpty()) return PlaceOrderResult.Failure("Your cart is empty.")

        val authToken = authRepository.authorizedToken()
            ?: return PlaceOrderResult.Failure("Sign in to place your order.")

        val anonKey = BuildConfig.SUPABASE_ANON_KEY
        val payload = PlaceOrderPayload(
            items = items,
            address = address,
            payment_method = "cod",
            customer_note = customerNote,
            coupon_code = pendingCouponCode?.takeIf { it.isNotBlank() }
        )

        // Reuse the key for retries of the exact same order so a lost response
        // cannot create a second order. A changed payload gets a fresh key.
        val signature = payload.toString()
        if (pendingOrderKey == null || pendingOrderSignature != signature) {
            pendingOrderKey = "CHK-" + UUID.randomUUID()
            pendingOrderSignature = signature
        }
        val request = PlaceOrderRequest(
            p_payload = payload,
            p_idempotency_key = pendingOrderKey.orEmpty()
        )

        return try {
            val response = api.placeOrder(apiKey = anonKey, auth = "Bearer $authToken", body = request)
            val body = response.body()
            when {
                !response.isSuccessful -> {
                    if (response.code() < 500) clearPendingOrderAttempt()
                    PlaceOrderResult.Failure(response.apiErrorMessage())
                }
                body == null ->
                    PlaceOrderResult.Failure("The store did not confirm your order. Please try again.")
                body.error != null -> {
                    clearPendingOrderAttempt()
                    PlaceOrderResult.Failure(body.message ?: body.error)
                }
                body.order_id.isNullOrBlank() || body.public_order_number.isNullOrBlank() -> {
                    clearPendingOrderAttempt()
                    PlaceOrderResult.Failure("The store did not confirm your order. Please try again.")
                }
                else -> {
                    clearPendingOrderAttempt()
                    PlaceOrderResult.Success(body)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PlaceOrderResult.Failure("Could not reach the store. Check your connection and try again.")
        }
    }

    private fun clearPendingOrderAttempt() {
        pendingOrderKey = null
        pendingOrderSignature = null
    }

    private fun Response<*>.apiErrorMessage(): String {
        val raw = errorBody()?.string().orEmpty()
        val parsed = runCatching {
            Json { ignoreUnknownKeys = true }.decodeFromString<ApiErrorResponse>(raw)
        }.getOrNull()
        return parsed?.message
            ?: parsed?.error_description
            ?: parsed?.error
            ?: "The request could not be completed. Please try again."
    }

    override fun applyCoupon(code: String) {
        pendingCouponCode = code.trim().uppercase()
        scheduleQuoteRefresh(debounceMillis = 0L)
    }

    override fun removeCoupon() {
        pendingCouponCode = null
        scheduleQuoteRefresh(debounceMillis = 0L)
    }

    private fun mapToQuoteBreakdown(response: QuoteCartResponse): QuoteBreakdown {
        return QuoteBreakdown(
            lines = response.lines.map { l ->
                QuoteBreakdown.Line(
                    productId = l.product_id,
                    name = l.name,
                    qtyBase = l.qty_base,
                    displayQty = l.display_qty,
                    unitPricePaise = l.unit_price_paise,
                    lineTotalPaise = l.line_total_paise,
                    available = l.available,
                    error = l.error
                )
            },
            itemSubtotalPaise = response.item_subtotal_paise,
            discountPaise = response.discount_paise,
            deliveryFeePaise = response.delivery_fee_paise,
            feeRuleNote = response.fee_rule_note,
            taxPaise = response.tax_paise,
            grandTotalPaise = response.grand_total_paise,
            belowChargeThreshold = response.below_charge_threshold,
            amountToFreeDelivery = response.amount_to_free_delivery,
            couponCode = response.coupon_code,
            couponApplied = response.coupon_applied,
            couponError = response.coupon_error
        )
    }
}

sealed interface PlaceOrderResult {
    data class Success(val response: PlaceOrderResponse) : PlaceOrderResult
    data class Failure(val message: String) : PlaceOrderResult
}

@Serializable
private data class ApiErrorResponse(
    val error: String? = null,
    val message: String? = null,
    val error_description: String? = null
)

interface CartRepository {
    val cartFlow: StateFlow<Cart>
    val quoteFlow: StateFlow<QuoteBreakdown?>
    fun refreshQuote() {}
    fun applyCoupon(code: String) {}
    fun removeCoupon() {}
    suspend fun getCart(): Cart
    suspend fun addItem(item: CartItem): Cart
    suspend fun updateQuantity(productId: String, qtyBase: Long): Cart
    suspend fun removeItem(productId: String): Cart
    suspend fun clearCart()
    suspend fun placeOrderRemote(address: AddressRequest, customerNote: String?): PlaceOrderResult
}
