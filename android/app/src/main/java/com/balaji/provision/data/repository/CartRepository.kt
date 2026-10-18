package com.balaji.provision.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.balaji.provision.data.network.QuoteCartRequest
import com.balaji.provision.data.network.QuoteCartResponse
import com.balaji.provision.data.network.CartItemRequest
import com.balaji.provision.data.network.SupabaseApi
import com.balaji.provision.domain.model.CartItem
import com.balaji.provision.domain.model.Cart
import com.balaji.provision.domain.model.QuoteBreakdown
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.flow.firstOrNull
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
        dataStore: DataStore<Preferences>,
        @Named("cartVersionKey") cartVersionKey: Preferences.Key<Int>
    ): CartRepository = CartRepositoryImpl(api, dataStore, cartVersionKey)
}

@Singleton
class CartRepositoryImpl @Inject constructor(
    private val api: SupabaseApi,
    private val dataStore: DataStore<Preferences>,
    @Named("cartVersionKey") private val cartVersionKey: Preferences.Key<Int>
) : CartRepository {
    
    override suspend fun getCart(): Cart {
        val version = dataStore.data.firstOrNull()?.get(cartVersionKey) ?: 0
        return Cart.empty()
    }
    
    override suspend fun addItem(item: CartItem): Cart {
        dataStore.edit { prefs ->
            val current = prefs[cartVersionKey] ?: 0
            prefs[cartVersionKey] = current + 1
        }
        return Cart.empty()
    }
    
    override suspend fun updateQuantity(productId: String, qtyBase: Long): Cart {
        dataStore.edit { prefs ->
            val current = prefs[cartVersionKey] ?: 0
            prefs[cartVersionKey] = current + 1
        }
        return Cart.empty()
    }
    
    override suspend fun removeItem(productId: String): Cart {
        dataStore.edit { prefs ->
            val current = prefs[cartVersionKey] ?: 0
            prefs[cartVersionKey] = current + 1
        }
        return Cart.empty()
    }
    
    override suspend fun clearCart() {
        dataStore.edit { prefs ->
            val current = prefs[cartVersionKey] ?: 0
            prefs[cartVersionKey] = current + 1
        }
    }
    
    override suspend fun getQuote(items: List<CartItem>): QuoteBreakdown {
        val request = QuoteCartRequest(
            items = items.map { CartItemRequest(it.productId, it.qtyBase) }
        )
        val response = api.quoteCart(apiKey = "", auth = "", body = request).body()!!
        return mapToQuoteBreakdown(response)
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
    suspend fun getCart(): Cart
    suspend fun addItem(item: CartItem): Cart
    suspend fun updateQuantity(productId: String, qtyBase: Long): Cart
    suspend fun removeItem(productId: String): Cart
    suspend fun clearCart()
    suspend fun getQuote(items: List<CartItem>): QuoteBreakdown
}