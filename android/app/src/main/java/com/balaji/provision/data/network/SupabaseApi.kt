package com.balaji.provision.data.network

import com.balaji.provision.domain.model.Category
import com.balaji.provision.domain.model.Product
import com.balaji.provision.domain.model.QuantityType
import com.balaji.provision.domain.model.RiderAddress
import com.balaji.provision.domain.model.RiderOrder
import com.balaji.provision.domain.model.RiderOrderItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface SupabaseApi {
    
    // Catalogue (Optimized Column Selection)
    @GET("rest/v1/products")
    suspend fun getProducts(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Query("select") select: String = "id,sku,name,description,category_id,image_path,quantity_type,price_paise,mrp_paise,price_basis_qty,unit_label,pack_size_label,step_qty,min_qty,max_qty,stock_qty,low_stock_threshold,active,available,variant_group_id,variant_label",
        @Query("order") order: String = "name.asc",
        @Query("limit") limit: Int = 100
    ): Response<List<ProductResponse>>
    
    @GET("rest/v1/categories")
    suspend fun getCategories(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Query("select") select: String = "id,name,slug,sort_order,active,image_path",
        @Query("order") order: String = "sort_order.asc"
    ): Response<List<CategoryResponse>>
    
    // Cart & Checkout RPCs
    @POST("rest/v1/rpc/quote_cart")
    suspend fun quoteCart(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Header("Prefer") prefer: String = "return=representation",
        @Body body: QuoteCartRequest
    ): Response<QuoteCartResponse>
    
    @POST("rest/v1/rpc/place_order")
    suspend fun placeOrder(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Header("Prefer") prefer: String = "return=representation",
        @Body body: PlaceOrderRequest
    ): Response<PlaceOrderResponse>

    @POST("rest/v1/rpc/cancel_my_order")
    suspend fun cancelMyOrder(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body body: CancelOrderRequest
    ): Response<CancelOrderResponse>

    @POST("rest/v1/rpc/delete_my_account")
    suspend fun deleteMyAccount(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body body: DeleteAccountRequest
    ): Response<DeleteAccountResponse>

    // Orders
    @GET("rest/v1/orders")
    suspend fun getOrders(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Query("customer_id") customerId: String? = null,
        @Query("select") select: String = "id,public_order_number,status,payment_method,payment_status,item_subtotal_paise,delivery_fee_paise,tax_paise,grand_total_paise,created_at,accepted_at,delivered_at,order_items(id,sku_snapshot,name_snapshot,quantity_type_snapshot,unit_label_snapshot,price_basis_qty_snapshot,qty_base,unit_price_paise,line_total_paise,removed_at,removed_reason)",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 100
    ): Response<List<OrderResponse>>

    // Rider delivery workflow
    @POST("rest/v1/rpc/get_rider_orders")
    suspend fun getRiderOrders(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body body: Map<String, String> = emptyMap()
    ): Response<List<RiderOrderResponse>>

    @POST("rest/v1/rpc/rider_update_status")
    suspend fun riderUpdateStatus(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body body: RiderUpdateStatusRequest
    ): Response<RiderUpdateStatusResponse>

    // Manager operations (staff + manager roles)
    @GET("rest/v1/orders")
    suspend fun getManagerOrders(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Query("status") status: String? = null,
        @Query("select") select: String =
            "id,public_order_number,status,grand_total_paise,created_at,customer_id,payment_method,payment_status,cod_collected_paise,address_snapshot,customer_note,item_subtotal_paise,delivery_fee_paise,tax_paise",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 200
    ): Response<List<ManagerOrderResponse>>

    @GET("rest/v1/order_items")
    suspend fun getManagerOrderItems(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Query("order_id") orderId: String,
        @Query("select") select: String =
            "id,sku_snapshot,name_snapshot,quantity_type_snapshot,unit_label_snapshot,qty_base,unit_price_paise,line_total_paise,removed_at,removed_reason",
        @Query("order") order: String? = null
    ): Response<List<ManagerOrderItemResponse>>

    @GET("rest/v1/profiles")
    suspend fun getActiveRiders(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Query("role") role: String = "eq.delivery",
        @Query("is_active") active: String = "eq.true",
        @Query("select") select: String = "id,display_name,login_id,phone,is_active",
        @Query("order") order: String = "display_name.asc"
    ): Response<List<RiderProfileResponse>>

    @POST("rest/v1/rpc/get_low_stock_products")
    suspend fun getLowStockProducts(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body body: Map<String, String> = emptyMap()
    ): Response<List<LowStockProductResponse>>

    @POST("rest/v1/rpc/remove_order_item")
    suspend fun removeOrderItem(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body body: RemoveOrderItemRequest
    ): Response<RemoveOrderItemResponse>

    @POST("rest/v1/rpc/transition_order")
    suspend fun transitionOrder(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body body: TransitionOrderRequest
    ): Response<ManagerActionResponse>

    @POST("rest/v1/rpc/assign_order")
    suspend fun assignOrder(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body body: AssignOrderRequest
    ): Response<ManagerActionResponse>

    @POST("rest/v1/rpc/adjust_stock")
    suspend fun adjustStock(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body body: AdjustStockRequest
    ): Response<AdjustStockResponse>

    @POST("rest/v1/rpc/update_product_price")
    suspend fun updateProductPrice(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body body: UpdateProductPriceRpcRequest
    ): Response<JsonObject>

    // Store settings (public read)
    @GET("rest/v1/store_settings")
    suspend fun getStoreSettings(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Query("select") select: String = "store_open,contact_phone,estimated_window_text,timezone,weekly_hours,delivery_fee_paise,delivery_charge_threshold_paise,free_delivery_threshold_paise,mid_tier_fee_paise,max_order_paise,cancellation_policy_text,tax_mode,tax_rate_bps,online_payment_enabled",
        @Query("limit") limit: Int = 1
    ): Response<List<StoreSettingsResponse>>
    
    @GET("rest/v1/orders")
    suspend fun getOrderById(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Query("id") id: String,
        @Query("select") select: String = "*,order_items(*)"
    ): Response<List<OrderResponse>>
    
    // Profile
    @GET("rest/v1/profiles")
    suspend fun getProfile(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Query("id") id: String,
        @Query("select") select: String
    ): Response<List<ProfileResponse>>

    @POST("rest/v1/rpc/update_my_profile")
    suspend fun updateMyProfile(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body body: UpdateMyProfileRequest
    ): Response<ProfileResponse>

    // Addresses
    @GET("rest/v1/addresses")
    suspend fun getAddresses(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Query("customer_id") customerId: String,
        @Query("select") select: String,
        @Query("order") order: String
    ): Response<List<AddressResponse>>

    @POST("rest/v1/rpc/save_my_address")
    suspend fun saveMyAddress(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body body: SaveMyAddressRequest
    ): Response<SaveMyAddressResponse>

    @POST("rest/v1/rpc/set_default_my_address")
    suspend fun setDefaultAddress(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body body: SetDefaultAddressRequest
    ): Response<SetDefaultAddressResponse>

    @POST("rest/v1/rpc/delete_my_address")
    suspend fun deleteAddress(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body body: DeleteAddressRequest
    ): Response<DeleteAddressResponse>
    
    @POST("rest/v1/addresses")
    suspend fun createAddress(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Header("Prefer") prefer: String = "return=representation",
        @Body body: AddressRequest
    ): Response<AddressResponse>
}

// Data classes matching Supabase responses
@Serializable
data class ProductResponse(
    val id: String,
    val sku: String,
    val name: String,
    val description: String? = null,
    val category_id: String,
    val image_path: String? = null,
    val quantity_type: String,
    val price_paise: Long,
    val mrp_paise: Long? = null,
    val price_basis_qty: Long,
    val unit_label: String,
    val pack_size_label: String? = null,
    val step_qty: Long,
    val min_qty: Long,
    val max_qty: Long? = null,
    val stock_qty: Long,
    val low_stock_threshold: Long,
    val active: Boolean,
    val available: Boolean,
    val variant_group_id: String? = null,
    val variant_label: String? = null
)

@Serializable
data class CategoryResponse(
    val id: String,
    val name: String,
    val slug: String,
    val sort_order: Int,
    val active: Boolean,
    val image_path: String? = null
)

@Serializable
data class QuoteCartRequest(
    val p_items: List<CartItemRequest>,
    val p_coupon_code: String? = null
)

@Serializable
data class CartItemRequest(
    val product_id: String,
    val qty_base: Long
)

@Serializable
data class QuoteCartResponse(
    val lines: List<QuoteLine>,
    val item_subtotal_paise: Long,
    val discount_paise: Long,
    val delivery_fee_paise: Long,
    val fee_rule_note: String,
    val tax_paise: Long,
    val grand_total_paise: Long,
    val below_charge_threshold: Boolean,
    val amount_to_free_delivery: Long,
    val coupon_code: String? = null,
    val coupon_applied: Boolean = false,
    val coupon_error: String? = null
)

@Serializable
data class QuoteLine(
    val product_id: String,
    val name: String,
    val qty_base: Long,
    val display_qty: String,
    val unit_price_paise: Long,
    val line_total_paise: Long,
    val available: Boolean,
    val error: String? = null
)

@Serializable
data class PlaceOrderRequest(
    val p_payload: PlaceOrderPayload,
    val p_idempotency_key: String
)

@Serializable
data class PlaceOrderPayload(
    val items: List<CartItemRequest>,
    val address: AddressRequest,
    val payment_method: String = "cod",
    val customer_note: String? = null,
    val coupon_code: String? = null
)


@Serializable
data class AddressRequest(
    val recipient_name: String,
    val phone: String,
    val address_lines: String,
    val landmark: String? = null,
    val area: String? = null,
    val city: String? = null,
    val state: String? = null,
    val postal_code: String,
    val lat: Double? = null,
    val lng: Double? = null
)

@Serializable
data class PlaceOrderResponse(
    val order_id: String? = null,
    val public_order_number: String? = null,
    val breakdown: QuoteCartResponse? = null,
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class CancelOrderRequest(
    val p_order_id: String,
    val p_reason: String? = null
)

@Serializable
data class CancelOrderResponse(
    val success: Boolean? = null,
    val order_id: String? = null,
    val status: String? = null,
    val stock_restored: Boolean? = null,
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class DeleteAccountRequest(
    val p_confirmation: String
)

@Serializable
data class DeleteAccountResponse(
    val success: Boolean? = null,
    val orders_retained: Int? = null,
    val addresses_deleted: Int? = null,
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class OrderResponse(
    val id: String,
    val public_order_number: String,
    val status: String,
    val item_subtotal_paise: Long? = null,
    val delivery_fee_paise: Long? = null,
    val tax_paise: Long? = null,
    val grand_total_paise: Long,
    val created_at: String,
    val accepted_at: String? = null,
    val delivered_at: String? = null,
    val order_items: List<OrderItemResponse>? = null
)

@Serializable
data class OrderItemResponse(
    val name_snapshot: String,
    val qty_base: Long,
    val line_total_paise: Long,
    val quantity_type_snapshot: String? = null,
    val unit_label_snapshot: String? = null,
    val removed_at: String? = null,
    val removed_reason: String? = null
)

@Serializable
data class StoreSettingsResponse(
    val store_open: Boolean = true,
    val contact_phone: String? = null,
    val estimated_window_text: String? = null,
    val timezone: String? = null,
    val weekly_hours: JsonObject? = null,
    val delivery_fee_paise: Long? = null,
    val delivery_charge_threshold_paise: Long? = null,
    val free_delivery_threshold_paise: Long? = null,
    val mid_tier_fee_paise: Long? = null,
    val max_order_paise: Long? = null,
    val cancellation_policy_text: String? = null,
    val tax_mode: String? = null,
    val tax_rate_bps: Int? = null,
    val online_payment_enabled: Boolean = false
)

@Serializable
data class ProfileResponse(
    val display_name: String? = null,
    val phone: String? = null,
    val role: String? = null,
    val is_active: Boolean? = null,
    val must_change_password: Boolean? = null,
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class UpdateMyProfileRequest(
    val p_display_name: String
)

@Serializable
data class AddressResponse(
    val id: String,
    val customer_id: String,
    val label: String,
    val recipient_name: String,
    val phone: String,
    val address_lines: String,
    val landmark: String? = null,
    val area: String? = null,
    val city: String? = null,
    val state: String? = null,
    val postal_code: String,
    val is_default: Boolean,
    val lat: Double? = null,
    val lng: Double? = null
)

@Serializable
data class RiderOrderResponse(
    val id: String,
    val public_order_number: String,
    val address_snapshot: RiderAddressSnapshot? = null,
    val payment_method: String? = null,
    val cod_collected_paise: Long? = null,
    val grand_total_paise: Long = 0L,
    val status: String,
    val customer_note: String? = null,
    val created_at: String = "",
    val items: List<RiderOrderItemResponse> = emptyList()
)

@Serializable
data class RiderAddressSnapshot(
    val recipient_name: String? = null,
    val phone: String? = null,
    val address_lines: String? = null,
    val landmark: String? = null,
    val area: String? = null,
    val city: String? = null,
    val state: String? = null,
    val postal_code: String? = null,
    val lat: Double? = null,
    val lng: Double? = null
)

@Serializable
data class RiderOrderItemResponse(
    val id: String? = null,
    val name: String,
    val qty: Long = 0L,
    val display_qty: String? = null,
    val unit: String? = null,
    val quantity_type: String? = null,
    val unitPrice: Long? = null,
    val total: Long? = null
)

@Serializable
data class RiderUpdateStatusRequest(
    val p_order_id: String,
    val p_to_status: String,
    val p_note: String? = null
)

@Serializable
data class RiderUpdateStatusResponse(
    val ok: Boolean? = null,
    val order_id: String? = null,
    val from_status: String? = null,
    val to_status: String? = null,
    val cod_collected_paise: Long? = null,
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class SaveMyAddressRequest(
    val p_id: String?,
    val p_label: String,
    val p_recipient_name: String,
    val p_phone: String,
    val p_address_lines: String,
    val p_landmark: String?,
    val p_area: String?,
    val p_city: String,
    val p_state: String,
    val p_postal_code: String,
    val p_is_default: Boolean,
    val p_lat: Double? = null,
    val p_lng: Double? = null
)

@Serializable
data class SaveMyAddressResponse(
    val address: AddressResponse? = null,
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class SetDefaultAddressRequest(
    val p_id: String
)

@Serializable
data class SetDefaultAddressResponse(
    val success: Boolean? = null,
    val address: AddressResponse? = null,
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class DeleteAddressRequest(
    val p_id: String
)

@Serializable
data class DeleteAddressResponse(
    val success: Boolean? = null,
    val error: String? = null,
    val message: String? = null
)

fun RiderOrderResponse.toDomain(): RiderOrder = RiderOrder(
    id = id,
    orderNumber = public_order_number,
    address = address_snapshot?.let { snapshot ->
        RiderAddress(
            recipientName = snapshot.recipient_name.orEmpty(),
            phone = snapshot.phone.orEmpty(),
            addressLines = snapshot.address_lines.orEmpty(),
            landmark = snapshot.landmark,
            area = snapshot.area,
            city = snapshot.city,
            state = snapshot.state,
            postalCode = snapshot.postal_code,
            lat = snapshot.lat,
            lng = snapshot.lng
        )
    },
    paymentMethod = payment_method.orEmpty(),
    codCollectedPaise = cod_collected_paise,
    grandTotalPaise = grand_total_paise,
    status = status,
    customerNote = customer_note,
    createdAt = created_at,
    items = items.map { item ->
        RiderOrderItem(
            name = item.name,
            displayQty = item.display_qty ?: "${item.qty} ${item.unit.orEmpty()}".trim(),
            lineTotalPaise = item.total ?: 0L
        )
    }
)

fun CategoryResponse.toDomain(): Category = Category(
    id = id,
    name = name,
    slug = slug,
    sortOrder = sort_order,
    active = active,
    imagePath = image_path?.takeIf { it.isNotBlank() }
)

fun ProductResponse.toDomain(): Product = Product(
    id = id,
    sku = sku,
    name = name,
    description = description,
    categoryId = category_id,
    imagePath = image_path,
    quantityType = try { QuantityType.valueOf(quantity_type.uppercase()) } catch (e: Exception) { QuantityType.PACKED },
    pricePaise = price_paise,
    mrpPaise = mrp_paise,
    priceBasisQty = price_basis_qty,
    unitLabel = unit_label,
    packSizeLabel = pack_size_label,
    stepQty = step_qty,
    minQty = min_qty,
    maxQty = max_qty,
    stockQty = stock_qty,
    lowStockThreshold = low_stock_threshold,
    active = active,
    available = available,
    variantGroupId = variant_group_id,
    variantLabel = variant_label
)
@Serializable
data class ManagerOrderResponse(
    val id: String,
    val public_order_number: String,
    val status: String,
    val grand_total_paise: Long = 0L,
    val created_at: String = "",
    val customer_id: String? = null,
    val payment_method: String? = null,
    val payment_status: String? = null,
    val cod_collected_paise: Long? = null,
    val address_snapshot: RiderAddressSnapshot? = null,
    val customer_note: String? = null,
    val item_subtotal_paise: Long = 0L,
    val delivery_fee_paise: Long = 0L,
    val tax_paise: Long = 0L
)

@Serializable
data class ManagerOrderItemResponse(
    val id: String,
    val sku_snapshot: String? = null,
    val name_snapshot: String,
    val quantity_type_snapshot: String? = null,
    val unit_label_snapshot: String? = null,
    val qty_base: Long = 0L,
    val unit_price_paise: Long = 0L,
    val line_total_paise: Long = 0L,
    val removed_at: String? = null,
    val removed_reason: String? = null
)

@Serializable
data class RiderProfileResponse(
    val id: String,
    val display_name: String? = null,
    val login_id: String? = null,
    val phone: String? = null,
    val is_active: Boolean = false
)

@Serializable
data class LowStockProductResponse(
    val id: String,
    val sku: String,
    val name: String,
    val stock_qty: Long,
    val low_stock_threshold: Long,
    val quantity_type: String,
    val unit_label: String,
    val price_basis_qty: Long = 0L,
    val price_paise: Long = 0L,
    val mrp_paise: Long? = null
)

@Serializable
data class RemoveOrderItemRequest(
    val p_order_id: String,
    val p_item_id: String,
    val p_reason: String
)

@Serializable
data class RemoveOrderItemResponse(
    val success: Boolean? = null,
    val order_cancelled: Boolean? = null,
    val item_subtotal_paise: Long? = null,
    val delivery_fee_paise: Long? = null,
    val tax_paise: Long? = null,
    val grand_total_paise: Long? = null,
    val payment_status: String? = null,
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class TransitionOrderRequest(
    val p_order_id: String,
    val p_to_status: String,
    val p_reason: String? = null
)

@Serializable
data class AssignOrderRequest(
    val p_order_id: String,
    val p_delivery_user_id: String
)

@Serializable
data class AdjustStockRequest(
    val p_product_id: String,
    val p_delta_qty: Long,
    val p_reason: String,
    val p_note: String? = null
)

@Serializable
data class UpdateProductPriceRpcRequest(
    val p_product_id: String,
    val p_price_paise: Long,
    val p_mrp_paise: Long? = null
)

@Serializable
data class AdjustStockResponse(
    val success: Boolean? = null,
    val product_id: String? = null,
    val old_stock: Long? = null,
    val new_stock: Long? = null,
    val delta_qty: Long? = null,
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class ManagerActionResponse(
    val ok: Boolean? = null,
    val success: Boolean? = null,
    val order_id: String? = null,
    val rider_id: String? = null,
    val from_status: String? = null,
    val to_status: String? = null,
    val error: String? = null,
    val message: String? = null
)
