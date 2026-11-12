package com.balaji.provision.data.repository

import com.balaji.provision.BuildConfig
import com.balaji.provision.data.auth.AuthRepository
import com.balaji.provision.data.network.AttachOrderProofRequest
import com.balaji.provision.data.network.CreateProductRequestRequest
import com.balaji.provision.data.network.OrderProofResponse
import com.balaji.provision.data.network.ProductRequestResponse
import com.balaji.provision.data.network.SubmitOrderReviewRequest
import com.balaji.provision.data.network.SignedProofUrlRequest
import com.balaji.provision.data.network.SupabaseApi
import com.balaji.provision.data.network.UpdateProductRequestRequest
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** A customer's ask that the store should stock a new item for sale. */
data class ProductRequest(
    val id: String,
    val itemName: String,
    val quantityText: String?,
    val note: String?,
    val photoPath: String?,
    val status: String,
    val staffReply: String?,
    val createdAt: String,
    val customerName: String? = null,
    val customerPhone: String? = null
) {
    val isOpen: Boolean get() = status == "new" || status == "accepted"
}

sealed interface RequestOutcome<out T> {
    data class Success<T>(val value: T) : RequestOutcome<T>
    data class Failure(val message: String) : RequestOutcome<Nothing>
}

interface RequestsRepository {
    suspend fun listRequests(staffView: Boolean): RequestOutcome<List<ProductRequest>>
    suspend fun createRequest(
        itemName: String,
        quantityText: String?,
        note: String?,
        photoPath: String? = null
    ): RequestOutcome<Unit>

    suspend fun updateRequest(
        requestId: String,
        status: String,
        staffReply: String?,
        linkedProductId: String? = null
    ): RequestOutcome<Unit>

    suspend fun submitReview(orderId: String, rating: Int, comment: String?): RequestOutcome<Unit>

    /** Stages (dispatch, delivery) that already have a proof for this order. */
    suspend fun loadProofs(orderId: String): RequestOutcome<Map<String, String>>

    /** A fresh, caller-authorized preview link; never use the proof kind as a URI. */
    suspend fun proofUrl(orderId: String, stage: String): RequestOutcome<String>

    suspend fun uploadProof(file: File, mimeType: String): RequestOutcome<String>

    suspend fun attachProof(
        orderId: String,
        stage: String,
        kind: String,
        path: String
    ): RequestOutcome<Unit>
}

@Module
@InstallIn(SingletonComponent::class)
object RequestsModule {
    @Provides
    @Singleton
    fun provideRequestsRepository(
        api: SupabaseApi,
        authRepository: AuthRepository
    ): RequestsRepository = RequestsRepositoryImpl(api, authRepository)
}

@Singleton
class RequestsRepositoryImpl @Inject constructor(
    private val api: SupabaseApi,
    private val authRepository: AuthRepository
) : RequestsRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun listRequests(staffView: Boolean): RequestOutcome<List<ProductRequest>> {
        val session = authRepository.session.value
            ?: return RequestOutcome.Failure("Sign in to view your requests.")
        val token = authRepository.authorizedToken()
            ?: return RequestOutcome.Failure("Your session expired. Please sign in again.")
        val apiKey = BuildConfig.SUPABASE_ANON_KEY
        return try {
            val response = if (staffView) {
                api.getStaffProductRequests(apiKey = apiKey, auth = "Bearer $token")
            } else {
                api.getProductRequests(apiKey = apiKey, auth = "Bearer $token")
            }
            if (!response.isSuccessful) {
                return RequestOutcome.Failure(errorMessage(response))
            }
            RequestOutcome.Success(response.body().orEmpty().map { it.toDomain() })
        } catch (e: Exception) {
            RequestOutcome.Failure("Could not load requests. Check your connection.")
        }
    }

    override suspend fun createRequest(
        itemName: String,
        quantityText: String?,
        note: String?,
        photoPath: String?
    ): RequestOutcome<Unit> {
        val token = authRepository.authorizedToken()
            ?: return RequestOutcome.Failure("Sign in to send a request.")
        return try {
            val response = api.createProductRequest(
                apiKey = BuildConfig.SUPABASE_ANON_KEY,
                auth = "Bearer $token",
                body = CreateProductRequestRequest(
                    p_item_name = itemName,
                    p_quantity_text = quantityText,
                    p_note = note,
                    p_photo_path = photoPath
                )
            )
            val body = response.body()
            when {
                !response.isSuccessful -> RequestOutcome.Failure(errorMessage(response))
                body?.error != null -> RequestOutcome.Failure(body.message ?: body.error)
                body?.request_id == null -> RequestOutcome.Failure("The store did not save the request.")
                else -> RequestOutcome.Success(Unit)
            }
        } catch (e: Exception) {
            RequestOutcome.Failure("Could not send the request. Check your connection.")
        }
    }

    override suspend fun updateRequest(
        requestId: String,
        status: String,
        staffReply: String?,
        linkedProductId: String?
    ): RequestOutcome<Unit> {
        val token = authRepository.authorizedToken()
            ?: return RequestOutcome.Failure("Sign in again to update requests.")
        return try {
            val response = api.updateProductRequest(
                apiKey = BuildConfig.SUPABASE_ANON_KEY,
                auth = "Bearer $token",
                body = UpdateProductRequestRequest(
                    p_request_id = requestId,
                    p_status = status,
                    p_staff_reply = staffReply,
                    p_linked_product_id = linkedProductId
                )
            )
            val body = response.body()
            when {
                !response.isSuccessful -> RequestOutcome.Failure(errorMessage(response))
                body?.error != null -> RequestOutcome.Failure(body.message ?: body.error)
                body?.status == null -> RequestOutcome.Failure("The request was not updated.")
                else -> RequestOutcome.Success(Unit)
            }
        } catch (e: Exception) {
            RequestOutcome.Failure("Could not update the request. Check your connection.")
        }
    }

    override suspend fun submitReview(
        orderId: String,
        rating: Int,
        comment: String?
    ): RequestOutcome<Unit> {
        val token = authRepository.authorizedToken()
            ?: return RequestOutcome.Failure("Sign in to review your order.")
        return try {
            val response = api.submitOrderReview(
                apiKey = BuildConfig.SUPABASE_ANON_KEY,
                auth = "Bearer $token",
                body = SubmitOrderReviewRequest(p_order_id = orderId, p_rating = rating, p_comment = comment)
            )
            val body = response.body()
            when {
                !response.isSuccessful -> RequestOutcome.Failure(errorMessage(response))
                body?.error != null -> RequestOutcome.Failure(body.message ?: body.error)
                body?.review_id == null -> RequestOutcome.Failure("The review was not saved.")
                else -> RequestOutcome.Success(Unit)
            }
        } catch (e: Exception) {
            RequestOutcome.Failure("Could not send the review. Check your connection.")
        }
    }


    override suspend fun loadProofs(orderId: String): RequestOutcome<Map<String, String>> {
        val token = authRepository.authorizedToken()
            ?: return RequestOutcome.Failure("Sign in to view order proof.")
        return try {
            val response = api.getOrderProofs(
                apiKey = BuildConfig.SUPABASE_ANON_KEY,
                auth = "Bearer $token",
                orderId = "eq.$orderId"
            )
            if (!response.isSuccessful) {
                RequestOutcome.Failure(errorMessage(response))
            } else {
                RequestOutcome.Success(
                    response.body().orEmpty().associate { it.stage to it.kind }
                )
            }
        } catch (e: Exception) {
            RequestOutcome.Failure("Could not load the order proof.")
        }
    }

    override suspend fun proofUrl(orderId: String, stage: String): RequestOutcome<String> {
        if (stage !in setOf("dispatch", "delivery")) {
            return RequestOutcome.Failure("Choose a dispatch or delivery proof.")
        }
        val token = authRepository.authorizedToken()
            ?: return RequestOutcome.Failure("Sign in to view order proof.")
        return try {
            val proof = api.getOrderProofs(
                apiKey = BuildConfig.SUPABASE_ANON_KEY,
                auth = "Bearer $token",
                orderId = "eq.$orderId"
            )
            if (!proof.isSuccessful) {
                return RequestOutcome.Failure("Could not load this proof. Please retry.")
            }
            val path = proof.body()?.firstOrNull { it.order_id == orderId && it.stage == stage }
                ?.path?.takeIf { it.isNotBlank() }
                ?: return RequestOutcome.Failure("No proof file is available for this order.")
            val signed = api.signOrderProof(
                apiKey = BuildConfig.SUPABASE_ANON_KEY,
                auth = "Bearer $token",
                path = path,
                body = SignedProofUrlRequest(expiresIn = 60)
            )
            if (!signed.isSuccessful) {
                return RequestOutcome.Failure("This proof is unavailable. Retry or sign in again.")
            }
            val relative = signed.body()?.signedURL
                ?.takeIf { it.startsWith("/object/sign/order-proofs/") }
                ?: return RequestOutcome.Failure("The store returned an invalid proof link.")
            RequestOutcome.Success(BuildConfig.SUPABASE_URL.trimEnd('/') + "/storage/v1" + relative)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (network: IOException) {
            RequestOutcome.Failure("Could not open the proof. Check your connection and retry.")
        } catch (unreadable: SerializationException) {
            RequestOutcome.Failure("The store returned unreadable proof details. Please retry.")
        }
    }

    override suspend fun uploadProof(file: File, mimeType: String): RequestOutcome<String> {
        val session = authRepository.session.value
            ?: return RequestOutcome.Failure("Sign in to upload proof.")
        val token = authRepository.authorizedToken()
            ?: return RequestOutcome.Failure("Your session expired. Please sign in again.")

        val safeMime = mimeType.lowercase()
        val extension = when {
            safeMime.contains("mp4") -> "mp4"
            safeMime.contains("quicktime") -> "mov"
            safeMime.contains("webm") -> "webm"
            safeMime.contains("png") -> "png"
            safeMime.contains("webp") -> "webp"
            else -> "jpg"
        }
        val path = "${session.userId}/proof/${UUID.randomUUID()}.$extension"

        return try {
            val response = api.uploadOrderProof(
                apiKey = BuildConfig.SUPABASE_ANON_KEY,
                auth = "Bearer $token",
                contentType = safeMime.ifBlank { "application/octet-stream" },
                path = path,
                body = file.asRequestBody(safeMime.ifBlank { "application/octet-stream" }.toMediaType())
            )
            if (response.isSuccessful) {
                RequestOutcome.Success(path)
            } else {
                RequestOutcome.Failure(errorMessage(response))
            }
        } catch (e: Exception) {
            RequestOutcome.Failure("Could not upload the proof. Check your connection.")
        }
    }

    override suspend fun attachProof(
        orderId: String,
        stage: String,
        kind: String,
        path: String
    ): RequestOutcome<Unit> {
        val token = authRepository.authorizedToken()
            ?: return RequestOutcome.Failure("Sign in again to save the proof.")
        return try {
            val response = api.attachOrderProof(
                apiKey = BuildConfig.SUPABASE_ANON_KEY,
                auth = "Bearer $token",
                body = AttachOrderProofRequest(
                    p_order_id = orderId,
                    p_stage = stage,
                    p_kind = kind,
                    p_path = path
                )
            )
            val body = response.body()
            when {
                !response.isSuccessful -> RequestOutcome.Failure(errorMessage(response))
                body?.error != null -> RequestOutcome.Failure(body.message ?: body.error)
                body?.path == null -> RequestOutcome.Failure("The proof was not saved.")
                else -> RequestOutcome.Success(Unit)
            }
        } catch (e: Exception) {
            RequestOutcome.Failure("Could not save the proof. Check your connection.")
        }
    }

    private fun ProductRequestResponse.toDomain() = ProductRequest(
        id = id,
        itemName = item_name,
        quantityText = quantity_text,
        note = note,
        photoPath = photo_path,
        status = status,
        staffReply = staff_reply,
        createdAt = created_at,
        customerName = profiles?.display_name,
        customerPhone = profiles?.phone
    )

    private fun errorMessage(response: Response<*>): String {
        val raw = response.errorBody()?.string().orEmpty()
        val parsed = runCatching { json.decodeFromString<RequestErrorBody>(raw) }.getOrNull()
        return parsed?.message
            ?: parsed?.hint
            ?: "The store rejected the request (HTTP ${response.code()})."
    }

    @Serializable
    private data class RequestErrorBody(
        val message: String? = null,
        val hint: String? = null,
        val details: String? = null
    )
}
