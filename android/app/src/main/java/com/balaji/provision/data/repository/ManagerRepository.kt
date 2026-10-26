package com.balaji.provision.data.repository

import com.balaji.provision.BuildConfig
import com.balaji.provision.data.auth.AuthRepository
import com.balaji.provision.data.network.AdjustStockRequest
import com.balaji.provision.data.network.AdjustStockResponse
import com.balaji.provision.data.network.AssignOrderRequest
import com.balaji.provision.data.network.LowStockProductResponse
import com.balaji.provision.data.network.ManagerActionResponse
import com.balaji.provision.data.network.ManagerOrderItemResponse
import com.balaji.provision.data.network.RemoveOrderItemRequest
import com.balaji.provision.data.network.RemoveOrderItemResponse
import com.balaji.provision.data.network.SupabaseApi
import com.balaji.provision.data.network.TransitionOrderRequest
import com.balaji.provision.data.network.toDomain
import com.balaji.provision.domain.model.LowStockProduct
import com.balaji.provision.domain.model.ManagerOrder
import com.balaji.provision.domain.model.ManagerOrderLine
import com.balaji.provision.domain.model.Product
import com.balaji.provision.domain.model.RiderOption
import com.balaji.provision.domain.model.formatBaseQuantity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ManagerResult<out T> {
    data class Success<T>(val value: T) : ManagerResult<T>
    data class Failure(val message: String) : ManagerResult<Nothing>
}

interface ManagerRepository {
    suspend fun loadOrders(): ManagerResult<List<ManagerOrder>>
    suspend fun loadOrderItems(orderId: String): ManagerResult<List<ManagerOrderLine>>
    suspend fun loadRiders(): ManagerResult<List<RiderOption>>
    suspend fun loadLowStock(): ManagerResult<List<LowStockProduct>>
    suspend fun loadAllProducts(): ManagerResult<List<com.balaji.provision.domain.model.Product>>
    suspend fun removeOrderItem(orderId: String, itemId: String, reason: String): ManagerResult<Boolean>
    suspend fun transitionOrder(orderId: String, toStatus: String, reason: String?): ManagerResult<Unit>
    suspend fun assignOrder(orderId: String, riderId: String): ManagerResult<Unit>
    suspend fun adjustStock(
        productId: String,
        deltaQty: Long,
        reason: String,
        note: String?
    ): ManagerResult<Long>
    suspend fun updateProductPrice(
        productId: String,
        pricePaise: Long,
        mrpPaise: Long?
    ): ManagerResult<Unit>
}

@Module
@InstallIn(SingletonComponent::class)
object ManagerModule {
    @Provides
    @Singleton
    fun provideManagerRepository(
        api: SupabaseApi,
        authRepository: AuthRepository
    ): ManagerRepository = ManagerRepositoryImpl(api, authRepository)
}

@Singleton
class ManagerRepositoryImpl @Inject constructor(
    private val api: SupabaseApi,
    private val authRepository: AuthRepository
) : ManagerRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun loadOrders(): ManagerResult<List<ManagerOrder>> = call { token ->
        val response = api.getManagerOrders(
            apiKey = BuildConfig.SUPABASE_ANON_KEY,
            auth = "Bearer $token",
            status = null,
            select = "id,public_order_number,status,grand_total_paise,created_at,customer_id," +
                "payment_method,payment_status,cod_collected_paise,address_snapshot," +
                "customer_note,item_subtotal_paise,delivery_fee_paise,tax_paise",
            order = "created_at.desc",
            limit = 200
        )
        if (!response.isSuccessful) return@call response.failure("Could not load store orders.")
        val orders = response.body() ?: return@call ManagerResult.Failure("Could not load store orders.")
        ManagerResult.Success(orders.map { order ->
            ManagerOrder(
                id = order.id,
                orderNumber = order.public_order_number,
                status = order.status,
                grandTotalPaise = order.grand_total_paise,
                createdAt = order.created_at,
                paymentMethod = order.payment_method,
                paymentStatus = order.payment_status,
                codCollectedPaise = order.cod_collected_paise,
                recipientName = order.address_snapshot?.recipient_name.orEmpty(),
                phone = order.address_snapshot?.phone.orEmpty(),
                addressLines = listOfNotNull(
                    order.address_snapshot?.address_lines,
                    order.address_snapshot?.landmark,
                    order.address_snapshot?.area,
                    order.address_snapshot?.city,
                    order.address_snapshot?.postal_code
                ).filter { it.isNotBlank() }.joinToString(", "),
                customerNote = order.customer_note,
                itemSubtotalPaise = order.item_subtotal_paise,
                deliveryFeePaise = order.delivery_fee_paise,
                taxPaise = order.tax_paise
            )
        })
    }

    override suspend fun loadOrderItems(orderId: String): ManagerResult<List<ManagerOrderLine>> = call { token ->
        val response = api.getManagerOrderItems(
            apiKey = BuildConfig.SUPABASE_ANON_KEY,
            auth = "Bearer $token",
            orderId = "eq.$orderId",
            select = "id,sku_snapshot,name_snapshot,quantity_type_snapshot,unit_label_snapshot," +
                "qty_base,unit_price_paise,line_total_paise,removed_at,removed_reason",
            order = null
        )
        if (!response.isSuccessful) return@call response.failure("Could not load order items.")
        val items = response.body() ?: return@call ManagerResult.Failure("Could not load order items.")
        ManagerResult.Success(items.map { it.toDomain() })
    }

    override suspend fun loadRiders(): ManagerResult<List<RiderOption>> = call { token ->
        val response = api.getActiveRiders(
            apiKey = BuildConfig.SUPABASE_ANON_KEY,
            auth = "Bearer $token",
            role = "eq.delivery",
            active = "eq.true",
            select = "id,display_name,login_id,phone,is_active",
            order = "display_name.asc"
        )
        if (!response.isSuccessful) return@call response.failure("Could not load delivery riders.")
        val riders = response.body() ?: return@call ManagerResult.Failure("Could not load delivery riders.")
        ManagerResult.Success(riders.map { rider ->
            RiderOption(
                id = rider.id,
                name = rider.display_name?.takeIf { it.isNotBlank() }
                    ?: rider.login_id.orEmpty().ifBlank { "Rider" },
                phone = rider.phone?.takeIf { it.isNotBlank() }
            )
        })
    }

    override suspend fun loadLowStock(): ManagerResult<List<LowStockProduct>> = call { token ->
        val response = api.getLowStockProducts(
            apiKey = BuildConfig.SUPABASE_ANON_KEY,
            auth = "Bearer $token",
            contentType = "application/json",
            body = emptyMap()
        )
        if (!response.isSuccessful) return@call response.failure("Could not load stock alerts.")
        val products = response.body() ?: return@call ManagerResult.Failure("Could not load stock alerts.")
        ManagerResult.Success(products.map { it.toDomain() })
    }

    override suspend fun removeOrderItem(
        orderId: String,
        itemId: String,
        reason: String
    ): ManagerResult<Boolean> = call { token ->
        val response = api.removeOrderItem(
            apiKey = BuildConfig.SUPABASE_ANON_KEY,
            auth = "Bearer $token",
            body = RemoveOrderItemRequest(
                p_order_id = orderId,
                p_item_id = itemId,
                p_reason = reason.trim()
            )
        )
        if (!response.isSuccessful) {
            return@call response.failure("Could not remove this item.")
        }
        val body = response.body()
            ?: return@call ManagerResult.Failure("Could not remove this item.")
        val message = body.errorMessage()
        if (message != null) return@call ManagerResult.Failure(message)
        ManagerResult.Success(body.order_cancelled == true)
    }

    override suspend fun transitionOrder(
        orderId: String,
        toStatus: String,
        reason: String?
    ): ManagerResult<Unit> = call { token ->
        val response = api.transitionOrder(
            apiKey = BuildConfig.SUPABASE_ANON_KEY,
            auth = "Bearer $token",
            body = TransitionOrderRequest(
                p_order_id = orderId,
                p_to_status = toStatus,
                p_reason = reason?.trim()?.takeIf { it.isNotEmpty() }
            )
        )
        if (!response.isSuccessful) return@call response.failure("Could not update this order.")
        val body = response.body() ?: return@call ManagerResult.Failure("Could not update this order.")
        val message = body.actionError()
        if (message != null) return@call ManagerResult.Failure(message)
        ManagerResult.Success(Unit)
    }

    override suspend fun assignOrder(orderId: String, riderId: String): ManagerResult<Unit> = call { token ->
        val response = api.assignOrder(
            apiKey = BuildConfig.SUPABASE_ANON_KEY,
            auth = "Bearer $token",
            body = AssignOrderRequest(
                p_order_id = orderId,
                p_delivery_user_id = riderId
            )
        )
        if (!response.isSuccessful) return@call response.failure("Could not assign this order.")
        val body = response.body() ?: return@call ManagerResult.Failure("Could not assign this order.")
        val message = body.actionError()
        if (message != null) return@call ManagerResult.Failure(message)
        ManagerResult.Success(Unit)
    }

    override suspend fun adjustStock(
        productId: String,
        deltaQty: Long,
        reason: String,
        note: String?
    ): ManagerResult<Long> = call { token ->
        val response = api.adjustStock(
            apiKey = BuildConfig.SUPABASE_ANON_KEY,
            auth = "Bearer $token",
            body = AdjustStockRequest(
                p_product_id = productId,
                p_delta_qty = deltaQty,
                p_reason = reason,
                p_note = note?.trim()?.takeIf { it.isNotEmpty() }
            )
        )
        if (!response.isSuccessful) return@call response.failure("Could not adjust stock.")
        val body = response.body() ?: return@call ManagerResult.Failure("Could not adjust stock.")
        val message = body.errorMessage()
        if (message != null) return@call ManagerResult.Failure(message)
        val newStock = body.new_stock
            ?: return@call ManagerResult.Failure("Stock changed but the new level was not returned.")
        ManagerResult.Success(newStock)
    }

    override suspend fun loadAllProducts(): ManagerResult<List<com.balaji.provision.domain.model.Product>> = call { token ->
        val response = api.getProducts(
            apiKey = BuildConfig.SUPABASE_ANON_KEY,
            auth = "Bearer $token",
            limit = 500
        )
        if (!response.isSuccessful) return@call response.failure("Could not load catalogue products.")
        val body = response.body() ?: return@call ManagerResult.Failure("Could not load catalogue products.")
        ManagerResult.Success(body.map { it.toDomain() })
    }

    override suspend fun updateProductPrice(
        productId: String,
        pricePaise: Long,
        mrpPaise: Long?
    ): ManagerResult<Unit> = call { token ->
        val response = api.updateProductPrice(
            apiKey = BuildConfig.SUPABASE_ANON_KEY,
            auth = "Bearer $token",
            body = com.balaji.provision.data.network.UpdateProductPriceRpcRequest(
                p_product_id = productId,
                p_price_paise = pricePaise,
                p_mrp_paise = mrpPaise
            )
        )
        if (!response.isSuccessful) return@call response.failure("Could not update product price.")
        ManagerResult.Success(Unit)
    }

    private suspend fun <T> call(
        block: suspend (String) -> ManagerResult<T>
    ): ManagerResult<T> {
        val token = authRepository.authorizedToken()
            ?: return ManagerResult.Failure("Your session expired. Sign in again.")
        return try {
            block(token)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ManagerResult.Failure("Could not reach the store. Check your connection and try again.")
        }
    }

    private fun Response<*>.failure(fallback: String): ManagerResult.Failure =
        ManagerResult.Failure(errorMessage(fallback))

    private fun Response<*>.errorMessage(fallback: String): String {
        val raw = errorBody()?.string().orEmpty()
        val parsed = runCatching { json.decodeFromString<ManagerApiError>(raw) }.getOrNull()
        return parsed?.message ?: parsed?.msg ?: parsed?.error ?: parsed?.details ?: fallback
    }

    private fun RemoveOrderItemResponse.errorMessage(): String? =
        error?.let { message ?: "Could not remove this item." }
            ?: message?.takeIf { success != true }

    private fun ManagerActionResponse.actionError(): String? {
        val okValue = ok ?: success
        if (error != null) return message ?: error
        if (okValue == false) return message ?: "The store rejected this action."
        return null
    }

    private fun AdjustStockResponse.errorMessage(): String? {
        if (error != null) return message ?: error
        if (success == false) return message ?: "The store rejected this stock change."
        return null
    }

    @Serializable
    private data class ManagerApiError(
        val error: String? = null,
        val message: String? = null,
        val msg: String? = null,
        val details: String? = null
    )
}

private fun ManagerOrderItemResponse.toDomain(): ManagerOrderLine = ManagerOrderLine(
    id = id,
    name = name_snapshot,
    quantityLabel = formatBaseQuantity(quantity_type_snapshot, qty_base, unit_label_snapshot),
    unitPricePaise = unit_price_paise,
    lineTotalPaise = line_total_paise,
    removed = !removed_at.isNullOrBlank(),
    removedReason = removed_reason
)

private fun LowStockProductResponse.toDomain(): LowStockProduct = LowStockProduct(
    id = id,
    name = name,
    sku = sku,
    stockQty = stock_qty,
    threshold = low_stock_threshold,
    quantityType = quantity_type,
    unitLabel = unit_label,
    pricePaise = price_paise,
    mrpPaise = mrp_paise
)
