package com.balaji.provision.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        @Named("cartVersionKey") cartVersionKey: Preferences.Key<Int>
    ): CartRepository = CartRepositoryImpl(api, authRepository, dataStore, cartVersionKey)
}

@Singleton
class CartRepositoryImpl @Inject constructor(
    private val api: SupabaseApi,
    private val authRepository: AuthRepository,
    private val dataStore: DataStore<Preferences>,
    @Named("cartVersionKey") private val cartVersionKey: Preferences.Key<Int>
) : CartRepository {
    
    private val inMemoryCartItems = mutableListOf<CartItem>()
    
    private val _cartFlow = MutableStateFlow<Cart>(Cart.empty())
    override val cartFlow: StateFlow<Cart> = _cartFlow.asStateFlow()
    
    private val _quoteFlow = MutableStateFlow<QuoteBreakdown?>(null)
    override val quoteFlow: StateFlow<QuoteBreakdown?> = _quoteFlow.asStateFlow()
    
    private fun notifyStateChanged() {
        _cartFlow.value = Cart(items = inMemoryCartItems.toList())
    }

    private suspend fun refreshQuote() {
        val items = inMemoryCartItems.toList()
        if (items.isEmpty()) {
            _quoteFlow.value = QuoteBreakdown()
            return
        }
        _quoteFlow.value = try {
            val request = QuoteCartRequest(items = items.map { CartItemRequest(it.productId, it.qtyBase) })
            val anonKey = BuildConfig.SUPABASE_ANON_KEY
            val response = api.quoteCart(apiKey = anonKey, auth = "Bearer $anonKey", body = request)
            val body = response.body()
            if (response.isSuccessful && body != null) mapToQuoteBreakdown(body) else null
        } catch (e: Exception) {
            null
        }
    }
    
    override suspend fun getCart(): Cart {
        return _cartFlow.value
    }
    
    override suspend fun addItem(item: CartItem): Cart {
        val existingIndex = inMemoryCartItems.indexOfFirst { it.productId == item.productId }
        if (existingIndex >= 0) {
            val existing = inMemoryCartItems[existingIndex]
            inMemoryCartItems[existingIndex] = existing.copy(qtyBase = existing.qtyBase + item.qtyBase)
        } else {
            inMemoryCartItems.add(item)
        }
        notifyStateChanged()
        refreshQuote()
        return _cartFlow.value
    }
    
    override suspend fun updateQuantity(productId: String, qtyBase: Long): Cart {
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
        notifyStateChanged()
        refreshQuote()
        return _cartFlow.value
    }
    
    override suspend fun removeItem(productId: String): Cart {
        inMemoryCartItems.removeAll { it.productId == productId }
        notifyStateChanged()
        refreshQuote()
        return _cartFlow.value
    }
    
    override suspend fun clearCart() {
        inMemoryCartItems.clear()
        notifyStateChanged()
        refreshQuote()
    }
    
    override suspend fun placeOrderRemote(address: AddressRequest, customerNote: String?): PlaceOrderResponse? {
        val items = inMemoryCartItems.map { CartItemRequest(it.productId, it.qtyBase) }
        if (items.isEmpty()) return null

        val authToken = authRepository.authorizedToken() ?: return null
        val anonKey = BuildConfig.SUPABASE_ANON_KEY
        val payload = PlaceOrderPayload(
            items = items,
            address = address,
            payment_method = "cod",
            customer_note = customerNote
        )
        val request = PlaceOrderRequest(
            p_payload = payload,
            p_idempotency_key = "IDEMP-" + System.currentTimeMillis() + "-" + (1000..9999).random()
        )

        return try {
            val response = api.placeOrder(apiKey = anonKey, auth = "Bearer $authToken", body = request)
            response.body().takeIf { response.isSuccessful }
        } catch (e: Exception) {
            null
        }
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
            amountToFreeDelivery = response.amount_to_free_delivery
        )
    }
}

interface CartRepository {
    val cartFlow: StateFlow<Cart>
    val quoteFlow: StateFlow<QuoteBreakdown?>
    suspend fun getCart(): Cart
    suspend fun addItem(item: CartItem): Cart
    suspend fun updateQuantity(productId: String, qtyBase: Long): Cart
    suspend fun removeItem(productId: String): Cart
    suspend fun clearCart()
    suspend fun placeOrderRemote(address: AddressRequest, customerNote: String?): PlaceOrderResponse?
}