package com.balaji.provision.data.repository

import androidx.datastore.preferences.rxjava3.RxDataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutations
import androidx.datastore.preferences.core.preferencesKey
import com.balaji.provision.data.network.QuoteCartRequest
import com.balaji.provision.data.network.QuoteCartResponse
import com.balaji.provision.data.network.CartItemRequest
import com.balaji.provision.data.network.SupabaseApi
import com.balaji.provision.domain.model.CartItem
import com.balaji.provision.domain.model.Cart
import com.balaji.provision.domain.model.QuoteBreakdown
import com.google.inject.Inject
import dagger.hilt.android.scopes.ActivityRetainedScoped
import dagger.hilt.InstallIn
import dagger.hilt.components.ActivityRetainedComponent
import dagger.Module
import dagger.Provides
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.core.Completable
import kotlinx.coroutines.tasks.await

@Module
@InstallIn(ActivityRetainedComponent::class)
class CartModule {
    
    @Provides
    fun provideCartRepository(
        api: SupabaseApi,
        dataStore: RxDataStore<Preferences>,
        cartVersionKey: androidx.datastore.preferences.core.Preferences.Key<Int>
    ) = CartRepositoryImpl(api, dataStore, cartVersionKey)
}

@ActivityRetainedScoped
class CartRepositoryImpl @Inject constructor(
    private val api: SupabaseApi,
    private val dataStore: RxDataStore<Preferences>,
    private val cartVersionKey: Preferences.Key<Int>
) : CartRepository {
    
    override suspend fun getCart(): Cart {
        val version = dataStore.data.firstOrNull()?.getInt(cartVersionKey) ?: 0
        // Cart is stored locally; we just return the persisted items
        // In a real implementation, you'd deserialize from DataStore
        return Cart.empty()
    }
    
    override suspend fun addItem(item: CartItem): Cart {
        // Increment version to trigger recomputation
        dataStore.updateDataAsync { it.mutations().setInt(cartVersionKey, (it[cartVersionKey] ?: 0) + 1) }.await()
        // Return updated cart
        return Cart.empty()
    }
    
    override suspend fun updateQuantity(productId: String, qtyBase: Long): Cart {
        dataStore.updateDataAsync { it.mutations().setInt(cartVersionKey, (it[cartVersionKey] ?: 0) + 1) }.await()
        return Cart.empty()
    }
    
    override suspend fun removeItem(productId: String): Cart {
        dataStore.updateDataAsync { it.mutations().setInt(cartVersionKey, (it[cartVersionKey] ?: 0) + 1) }.await()
        return Cart.empty()
    }
    
    override suspend fun clearCart() {
        dataStore.updateDataAsync { it.mutations().setInt(cartVersionKey, (it[cartVersionKey] ?: 0) + 1) }.await()
    }
    
    override suspend fun getQuote(items: List<CartItem>): QuoteBreakdown {
        val request = QuoteCartRequest(
            items = items.map { CartItemRequest(it.productId, it.qtyBase) }
        )
        val response = api.quoteCart(request).body()!!
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