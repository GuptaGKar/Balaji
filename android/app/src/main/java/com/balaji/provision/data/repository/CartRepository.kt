package com.balaji.provision.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.balaji.provision.BuildConfig
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
import com.balaji.provision.domain.model.QuantityType
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
        catalogRepository: CatalogRepository,
        dataStore: DataStore<Preferences>,
        @Named("cartVersionKey") cartVersionKey: Preferences.Key<Int>
    ): CartRepository = CartRepositoryImpl(api, catalogRepository, dataStore, cartVersionKey)
}

@Singleton
class CartRepositoryImpl @Inject constructor(
    private val api: SupabaseApi,
    private val catalogRepository: CatalogRepository,
    private val dataStore: DataStore<Preferences>,
    @Named("cartVersionKey") private val cartVersionKey: Preferences.Key<Int>
) : CartRepository {
    
    private val inMemoryCartItems = mutableListOf<CartItem>()
    
    private val _cartFlow = MutableStateFlow<Cart>(Cart.empty())
    override val cartFlow: StateFlow<Cart> = _cartFlow.asStateFlow()
    
    private val _quoteFlow = MutableStateFlow<QuoteBreakdown?>(null)
    override val quoteFlow: StateFlow<QuoteBreakdown?> = _quoteFlow.asStateFlow()
    
    private fun notifyStateChanged() {
        val cart = Cart(items = inMemoryCartItems.toList())
        _cartFlow.value = cart
        _quoteFlow.value = calculateLocalQuote(inMemoryCartItems)
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
        return _cartFlow.value
    }
    
    override suspend fun removeItem(productId: String): Cart {
        inMemoryCartItems.removeAll { it.productId == productId }
        notifyStateChanged()
        return _cartFlow.value
    }
    
    override suspend fun clearCart() {
        inMemoryCartItems.clear()
        notifyStateChanged()
    }
    
    override suspend fun getQuote(items: List<CartItem>): QuoteBreakdown {
        if (items.isEmpty()) {
            return QuoteBreakdown()
        }
        try {
            val request = QuoteCartRequest(
                items = items.map { CartItemRequest(it.productId, it.qtyBase) }
            )
            val anonKey = BuildConfig.SUPABASE_ANON_KEY
            val response = api.quoteCart(apiKey = anonKey, auth = "Bearer $anonKey", body = request)
            if (response.isSuccessful && response.body() != null) {
                return mapToQuoteBreakdown(response.body()!!)
            }
        } catch (e: Exception) {
            // Fallback for offline preview
        }
        return calculateLocalQuote(items)
    }

    override suspend fun placeOrderRemote(address: AddressRequest, customerNote: String?): PlaceOrderResponse? {
        val items = inMemoryCartItems.map { CartItemRequest(it.productId, it.qtyBase) }
        if (items.isEmpty()) return null

        val payload = PlaceOrderPayload(
            items = items,
            address = address,
            payment_method = "cod",
            customer_note = customerNote
        )
        val request = PlaceOrderRequest(
            payload = payload,
            idempotency_key = "IDEMP-" + System.currentTimeMillis() + "-" + (1000..9999).random()
        )

        val anonKey = BuildConfig.SUPABASE_ANON_KEY
        return try {
            val response = api.placeOrder(apiKey = anonKey, auth = "Bearer $anonKey", body = request)
            if (response.isSuccessful && response.body() != null) {
                response.body()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun calculateLocalQuote(items: List<CartItem>): QuoteBreakdown {
        val lines = items.mapNotNull { item ->
            val product = catalogRepository.findProductById(item.productId) ?: return@mapNotNull null
            
            val lineTotal: Long
            val displayQty: String
            val unitPriceVal: Long
            
            if (product.quantityType == QuantityType.WEIGHTED) {
                val grams = item.qtyBase
                val kg = grams / 1000.0
                displayQty = if (grams >= 1000 && grams % 500 == 0L) {
                    if (kg == kg.toLong().toDouble()) "${kg.toLong()} kg" else "${kg} kg"
                } else {
                    "${grams} g"
                }
                unitPriceVal = product.pricePaise
                lineTotal = (product.pricePaise.toDouble() * (grams.toDouble() / product.priceBasisQty.toDouble())).toLong()
            } else {
                val packs = item.qtyBase
                displayQty = if (packs == 1L) "1 pack" else "${packs} packs"
                unitPriceVal = product.pricePaise
                lineTotal = product.pricePaise * packs
            }
            
            QuoteBreakdown.Line(
                productId = product.id,
                name = product.name,
                qtyBase = item.qtyBase,
                displayQty = displayQty,
                unitPricePaise = unitPriceVal,
                lineTotalPaise = lineTotal,
                available = product.isInStock,
                error = null
            )
        }
        
        val itemSubtotal = lines.sumOf { it.lineTotalPaise }
        val freeDeliveryThreshold = 25000L // ₹250 threshold
        val deliveryFee = if (itemSubtotal >= freeDeliveryThreshold || itemSubtotal == 0L) 0L else 3000L // ₹30 fee
        val belowThreshold = itemSubtotal < freeDeliveryThreshold && itemSubtotal > 0L
        val amountToFree = if (belowThreshold) freeDeliveryThreshold - itemSubtotal else 0L
        val grandTotal = itemSubtotal + deliveryFee
        
        return QuoteBreakdown(
            lines = lines,
            itemSubtotalPaise = itemSubtotal,
            discountPaise = 0,
            deliveryFeePaise = deliveryFee,
            feeRuleNote = if (belowThreshold) "Delivery charge applicable for orders below ₹250" else "Free local delivery unlocked!",
            taxPaise = 0,
            grandTotalPaise = grandTotal,
            belowChargeThreshold = belowThreshold,
            amountToFreeDelivery = amountToFree
        )
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
    suspend fun getQuote(items: List<CartItem>): QuoteBreakdown
    suspend fun placeOrderRemote(address: AddressRequest, customerNote: String?): PlaceOrderResponse?
}