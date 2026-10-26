package com.balaji.provision.data.repository

import com.balaji.provision.BuildConfig
import com.balaji.provision.data.auth.AuthRepository
import com.balaji.provision.data.network.RiderUpdateStatusRequest
import com.balaji.provision.data.network.SupabaseApi
import com.balaji.provision.data.network.toDomain
import com.balaji.provision.domain.model.RiderOrder
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

sealed interface RiderResult<out T> {
    data class Success<T>(val value: T) : RiderResult<T>
    data class Failure(val message: String) : RiderResult<Nothing>
}

interface RiderRepository {
    suspend fun loadOrders(): RiderResult<List<RiderOrder>>
    suspend fun updateStatus(orderId: String, toStatus: String, note: String? = null): RiderResult<Unit>
}

@Module
@InstallIn(SingletonComponent::class)
object RiderModule {
    @Provides
    @Singleton
    fun provideRiderRepository(
        api: SupabaseApi,
        authRepository: AuthRepository
    ): RiderRepository = RiderRepositoryImpl(api, authRepository)
}

@Singleton
class RiderRepositoryImpl @Inject constructor(
    private val api: SupabaseApi,
    private val authRepository: AuthRepository
) : RiderRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun loadOrders(): RiderResult<List<RiderOrder>> {
        val token = authRepository.authorizedToken()
            ?: return RiderResult.Failure("Your session expired. Sign in again.")
        return try {
            val response = api.getRiderOrders(
                apiKey = BuildConfig.SUPABASE_ANON_KEY,
                auth = "Bearer $token"
            )
            if (!response.isSuccessful) {
                return RiderResult.Failure(response.errorMessage("Could not load your deliveries."))
            }
            val orders = response.body()
                ?: return RiderResult.Failure("Could not load your deliveries.")
            RiderResult.Success(orders.map { it.toDomain() })
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            RiderResult.Failure("Could not reach the store. Check your connection and try again.")
        }
    }

    override suspend fun updateStatus(
        orderId: String,
        toStatus: String,
        note: String?
    ): RiderResult<Unit> {
        val trimmedNote = note?.trim()?.takeIf(String::isNotEmpty)
        if (toStatus == "delivery_failed" && trimmedNote == null) {
            return RiderResult.Failure("A reason is required for a failed delivery.")
        }
        val token = authRepository.authorizedToken()
            ?: return RiderResult.Failure("Your session expired. Sign in again.")
        return try {
            val response = api.riderUpdateStatus(
                apiKey = BuildConfig.SUPABASE_ANON_KEY,
                auth = "Bearer $token",
                body = RiderUpdateStatusRequest(
                    p_order_id = orderId,
                    p_to_status = toStatus,
                    p_note = trimmedNote
                )
            )
            if (!response.isSuccessful) {
                return RiderResult.Failure(response.errorMessage("Could not update this delivery."))
            }
            val body = response.body()
                ?: return RiderResult.Failure("Could not update this delivery.")
            if (body.error != null || body.ok != true) {
                return RiderResult.Failure(
                    body.message ?: body.error ?: "Could not update this delivery."
                )
            }
            RiderResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            RiderResult.Failure("Could not reach the store. Check your connection and try again.")
        }
    }

    private fun Response<*>.errorMessage(fallback: String): String {
        val raw = errorBody()?.string().orEmpty()
        val parsed = runCatching { json.decodeFromString<ApiError>(raw) }.getOrNull()
        return parsed?.message
            ?: parsed?.msg
            ?: parsed?.error
            ?: fallback
    }

    @Serializable
    private data class ApiError(
        val error: String? = null,
        val message: String? = null,
        val msg: String? = null
    )
}
