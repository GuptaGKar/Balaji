package com.balaji.provision.data.repository

import com.balaji.provision.data.auth.AuthRepository
import com.balaji.provision.data.network.AdjustStockRequest
import com.balaji.provision.data.network.AdjustStockResponse
import com.balaji.provision.data.network.AssignOrderRequest
import com.balaji.provision.data.network.LowStockProductResponse
import com.balaji.provision.data.network.ManagerActionResponse
import com.balaji.provision.data.network.ManagerOrderItemResponse
import com.balaji.provision.data.network.ManagerOrderResponse
import com.balaji.provision.data.network.RiderAddressSnapshot
import com.balaji.provision.data.network.RemoveOrderItemRequest
import com.balaji.provision.data.network.RemoveOrderItemResponse
import com.balaji.provision.data.network.RiderProfileResponse
import com.balaji.provision.data.network.SupabaseApi
import com.balaji.provision.data.network.TransitionOrderRequest
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Response

class ManagerRepositoryTest {

    private val api: SupabaseApi = mock()
    private val auth: AuthRepository = mock()
    private lateinit var repository: ManagerRepositoryImpl

    @Before
    fun setUp() {
        repository = ManagerRepositoryImpl(api, auth)
    }

    @Test
    fun `load orders maps customer address and totals`() = runTest {
        signIn()
        whenever(
            api.getManagerOrders(any(), any(), anyOrNull(), any(), any(), any())
        ).thenReturn(
            Response.success(
                listOf(
                    ManagerOrderResponse(
                        id = "order-1",
                        public_order_number = "BPS-1001",
                        status = "pending_review",
                        grand_total_paise = 24900,
                        created_at = "2026-10-23T04:30:00Z",
                        payment_method = "cod",
                        payment_status = "unpaid",
                        address_snapshot = RiderAddressSnapshot(
                            recipient_name = "Asha",
                            phone = "9876543210",
                            address_lines = "12, MG Road",
                            city = "Pune",
                            postal_code = "411005"
                        ),
                        item_subtotal_paise = 22500,
                        delivery_fee_paise = 2400
                    )
                )
            )
        )

        val result = repository.loadOrders()

        assertTrue(result is ManagerResult.Success)
        val order = (result as ManagerResult.Success).value.single()
        assertEquals("BPS-1001", order.orderNumber)
        assertEquals("Asha", order.recipientName)
        assertEquals("12, MG Road, Pune, 411005", order.addressLines)
        assertEquals("pending_review", order.status)
        assertEquals(24900, order.grandTotalPaise)
        assertTrue(order.canAssignRider.not())
        assertEquals(
            listOf("accepted", "rejected"),
            order.actions.map { it.toStatus }
        )
    }

    @Test
    fun `order items format weighted quantity in integer units`() = runTest {
        signIn()
        whenever(
            api.getManagerOrderItems(any(), any(), any(), any(), anyOrNull())
        ).thenReturn(
            Response.success(
                listOf(
                    ManagerOrderItemResponse(
                        id = "item-1",
                        name_snapshot = "Basmati rice",
                        quantity_type_snapshot = "weighted",
                        unit_label_snapshot = "kg",
                        qty_base = 1500,
                        unit_price_paise = 12000,
                        line_total_paise = 18000
                    )
                )
            )
        )

        val result = repository.loadOrderItems("order-1")

        assertTrue(result is ManagerResult.Success)
        val item = (result as ManagerResult.Success).value.single()
        assertEquals("1 kg 500 g", item.quantityLabel)
        assertEquals(18000, item.lineTotalPaise)
    }

    @Test
    fun `load order items does not order by the missing created_at column`() = runTest {
        signIn()
        whenever(
            api.getManagerOrderItems(any(), any(), any(), any(), anyOrNull())
        ).thenReturn(Response.success(emptyList()))

        repository.loadOrderItems("order-1")

        verify(api).getManagerOrderItems(any(), any(), any(), any(), isNull())
    }

    @Test
    fun `load low stock maps threshold and packed quantity`() = runTest {
        signIn()
        whenever(
            api.getLowStockProducts(any(), any(), any(), any())
        ).thenReturn(
            Response.success(
                listOf(
                    LowStockProductResponse(
                        id = "product-1",
                        sku = "SKU-1",
                        name = "Toor dal 1kg",
                        stock_qty = 2,
                        low_stock_threshold = 5,
                        quantity_type = "packed",
                        unit_label = "pack"
                    )
                )
            )
        )

        val result = repository.loadLowStock()

        assertTrue(result is ManagerResult.Success)
        val product = (result as ManagerResult.Success).value.single()
        assertEquals("2 x pack", product.quantityLabel)
        assertEquals(5, product.threshold)
    }

    @Test
    fun `load riders omits blank names and phones`() = runTest {
        signIn()
        whenever(
            api.getActiveRiders(any(), any(), any(), any(), any(), any())
        ).thenReturn(
            Response.success(
                listOf(
                    RiderProfileResponse(
                        id = "rider-1",
                        display_name = "",
                        login_id = "RIDER1",
                        phone = null,
                        is_active = true
                    )
                )
            )
        )

        val result = repository.loadRiders()

        assertTrue(result is ManagerResult.Success)
        val rider = (result as ManagerResult.Success).value.single()
        assertEquals("RIDER1", rider.name)
        assertEquals(null, rider.phone)
    }

    @Test
    fun `remove unavailable item sends a reason and reports cancellation`() = runTest {
        signIn()
        whenever(api.removeOrderItem(any(), any(), any(), any())).thenReturn(
            Response.success(RemoveOrderItemResponse(success = true, order_cancelled = true))
        )

        val result = repository.removeOrderItem("order-1", "item-1", "  out of stock  ")

        assertEquals(ManagerResult.Success(true), result)
        val captor = argumentCaptor<RemoveOrderItemRequest>()
        verify(api).removeOrderItem(any(), any(), any(), captor.capture())
        assertEquals("out of stock", captor.firstValue.p_reason)
    }

    @Test
    fun `remove unavailable item surfaces server rejection`() = runTest {
        signIn()
        whenever(api.removeOrderItem(any(), any(), any(), any())).thenReturn(
            Response.success(
                RemoveOrderItemResponse(
                    error = "INVALID_STATUS",
                    message = "Item removal is only allowed before dispatch"
                )
            )
        )

        val result = repository.removeOrderItem("order-1", "item-1", "out of stock")

        assertTrue(result is ManagerResult.Failure)
        assertEquals(
            "Item removal is only allowed before dispatch",
            (result as ManagerResult.Failure).message
        )
    }

    @Test
    fun `transition sends reason and surfaces server rejection`() = runTest {
        signIn()
        val body = """{"error":"INVALID_TRANSITION","message":"Invalid status transition"}"""
            .toResponseBody("application/json".toMediaType())
        whenever(api.transitionOrder(any(), any(), any(), any()))
            .thenReturn(Response.error(400, body))

        val result = repository.transitionOrder("order-1", "cancelled", "  out of stock  ")

        assertTrue(result is ManagerResult.Failure)
        assertEquals("Invalid status transition", (result as ManagerResult.Failure).message)
        val captor = argumentCaptor<TransitionOrderRequest>()
        verify(api).transitionOrder(any(), any(), any(), captor.capture())
        assertEquals("out of stock", captor.firstValue.p_reason)
    }

    @Test
    fun `assign sends selected rider`() = runTest {
        signIn()
        whenever(api.assignOrder(any(), any(), any(), any()))
            .thenReturn(Response.success(ManagerActionResponse(success = true)))

        val result = repository.assignOrder("order-1", "rider-1")

        assertEquals(ManagerResult.Success(Unit), result)
        val captor = argumentCaptor<AssignOrderRequest>()
        verify(api).assignOrder(any(), any(), any(), captor.capture())
        assertEquals("rider-1", captor.firstValue.p_delivery_user_id)
    }

    @Test
    fun `adjust stock returns the new level`() = runTest {
        signIn()
        whenever(api.adjustStock(any(), any(), any(), any()))
            .thenReturn(Response.success(AdjustStockResponse(success = true, new_stock = 12)))

        val result = repository.adjustStock("product-1", 10, "manual_adjustment", "delivery")

        assertTrue(result is ManagerResult.Success)
        assertEquals(12L, (result as ManagerResult.Success).value)
        val captor = argumentCaptor<AdjustStockRequest>()
        verify(api).adjustStock(any(), any(), any(), captor.capture())
        assertEquals(10, captor.firstValue.p_delta_qty)
        assertEquals("delivery", captor.firstValue.p_note)
    }

    @Test
    fun `expired session does not call the api`() = runTest {
        whenever(auth.authorizedToken()).thenReturn(null)

        val result = repository.loadOrders()

        assertTrue(result is ManagerResult.Failure)
        assertTrue((result as ManagerResult.Failure).message.contains("session expired"))
    }

    private suspend fun signIn() {
        whenever(auth.authorizedToken()).thenReturn("access")
    }
}
