package com.balaji.provision.data.network

import com.balaji.provision.domain.model.Category
import com.balaji.provision.domain.model.Product
import com.balaji.provision.domain.model.QuantityType
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
        @Query("select") select: String = "id,sku,name,description,category_id,image_path,quantity_type,price_paise,price_basis_qty,unit_label,pack_size_label,step_qty,min_qty,max_qty,stock_qty,low_stock_threshold,active,available",
        @Query("order") order: String = "name.asc",
        @Query("limit") limit: Int = 100
    ): Response<List<ProductResponse>>
    
    @GET("rest/v1/categories")
    suspend fun getCategories(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Query("select") select: String = "id,name,slug,sort_order,active",
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

    // Direct REST Order Creation Fallbacks
    @POST("rest/v1/orders")
    suspend fun createOrderDirect(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Header("Prefer") prefer: String = "return=representation",
        @Body body: DirectOrderRequest
    ): Response<List<OrderResponse>>

    @POST("rest/v1/order_items")
    suspend fun createOrderItemsDirect(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Header("Prefer") prefer: String = "return=representation",
        @Body body: List<DirectOrderItemRequest>
    ): Response<List<OrderItemResponse>>
    
    // Orders
    @GET("rest/v1/orders")
    suspend fun getOrders(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Query("select") select: String = "*,order_items(*)",
        @Query("order") order: String = "created_at.desc"
    ): Response<List<OrderResponse>>
    
    @GET("rest/v1/orders")
    suspend fun getOrderById(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Query("id") id: String,
        @Query("select") select: String = "*,order_items(*)"
    ): Response<List<OrderResponse>>
    
    // Addresses
    @GET("rest/v1/addresses")
    suspend fun getAddresses(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Query("customer_id") customerId: String,
        @Query("select") select: String = "*"
    ): Response<List<AddressResponse>>
    
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
    val price_basis_qty: Long,
    val unit_label: String,
    val pack_size_label: String? = null,
    val step_qty: Long,
    val min_qty: Long,
    val max_qty: Long? = null,
    val stock_qty: Long,
    val low_stock_threshold: Long,
    val active: Boolean,
    val available: Boolean
)

@Serializable
data class CategoryResponse(
    val id: String,
    val name: String,
    val slug: String,
    val sort_order: Int,
    val active: Boolean
)

@Serializable
data class QuoteCartRequest(
    val items: List<CartItemRequest>
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
    val amount_to_free_delivery: Long
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
data class DirectOrderRequest(
    val public_order_number: String,
    val address_snapshot: AddressRequest,
    val status: String = "pending_review",
    val payment_method: String = "cod",
    val payment_status: String = "unpaid",
    val item_subtotal_paise: Long,
    val discount_paise: Long = 0,
    val delivery_fee_paise: Long = 0,
    val tax_paise: Long = 0,
    val grand_total_paise: Long,
    val customer_note: String? = null
)

@Serializable
data class DirectOrderItemRequest(
    val order_id: String,
    val product_id: String? = null,
    val sku_snapshot: String,
    val name_snapshot: String,
    val quantity_type_snapshot: String,
    val unit_label_snapshot: String,
    val price_basis_qty_snapshot: Long,
    val qty_base: Long,
    val unit_price_paise: Long,
    val line_total_paise: Long
)

@Serializable
data class PlaceOrderPayload(
    val items: List<CartItemRequest>,
    val address: AddressRequest,
    val payment_method: String = "cod",
    val customer_note: String? = null
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
    val postal_code: String
)

@Serializable
data class PlaceOrderResponse(
    val order_id: String,
    val public_order_number: String,
    val breakdown: QuoteCartResponse? = null
)

@Serializable
data class OrderResponse(
    val id: String,
    val public_order_number: String,
    val customer_id: String? = null,
    val address_snapshot: JsonObject,
    val status: String,
    val payment_method: String,
    val payment_status: String,
    val item_subtotal_paise: Long,
    val discount_paise: Long,
    val delivery_fee_paise: Long,
    val tax_paise: Long,
    val grand_total_paise: Long,
    val fee_breakdown: JsonObject? = null,
    val customer_note: String? = null,
    val assigned_to: String? = null,
    val created_at: String,
    val accepted_at: String? = null,
    val delivered_at: String? = null,
    val order_items: List<OrderItemResponse>? = null
)

@Serializable
data class OrderItemResponse(
    val id: String,
    val order_id: String,
    val product_id: String? = null,
    val sku_snapshot: String,
    val name_snapshot: String,
    val quantity_type_snapshot: String,
    val unit_label_snapshot: String,
    val price_basis_qty_snapshot: Long,
    val qty_base: Long,
    val unit_price_paise: Long,
    val line_total_paise: Long,
    val removed_at: String? = null,
    val removed_reason: String? = null
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
    val is_default: Boolean
)

fun CategoryResponse.toDomain(): Category = Category(
    id = id,
    name = name,
    slug = slug,
    sortOrder = sort_order,
    active = active
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
    priceBasisQty = price_basis_qty,
    unitLabel = unit_label,
    packSizeLabel = pack_size_label,
    stepQty = step_qty,
    minQty = min_qty,
    maxQty = max_qty,
    stockQty = stock_qty,
    lowStockThreshold = low_stock_threshold,
    active = active,
    available = available
)