package com.balaji.provision.data.repository

import com.balaji.provision.BuildConfig
import com.balaji.provision.data.auth.AuthRepository
import com.balaji.provision.data.network.FavouriteRequest
import com.balaji.provision.data.network.SupabaseApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

data class FavouriteProduct(
    val productId: String,
    val name: String,
    val pricePaise: Long,
    val unitLabel: String,
    val imagePath: String?,
    val mrpPaise: Long? = null
) {
    val hasDiscount: Boolean
        get() = mrpPaise != null && mrpPaise > pricePaise

    val discountPercent: Int
        get() {
            val list = mrpPaise ?: return 0
            if (list <= pricePaise || list <= 0L) return 0
            return ((list - pricePaise) * 100 / list).toInt()
        }
}

interface FavouritesRepository {
    suspend fun load(): RequestOutcome<List<FavouriteProduct>>
    suspend fun add(productId: String): RequestOutcome<Unit>
    suspend fun remove(productId: String): RequestOutcome<Unit>
}

@Module
@InstallIn(SingletonComponent::class)
object FavouritesModule {
    @Provides
    @Singleton
    fun provideFavouritesRepository(
        api: SupabaseApi,
        authRepository: AuthRepository
    ): FavouritesRepository = FavouritesRepositoryImpl(api, authRepository)
}

/** F3 favourites: owner-only rows, one per (customer, product). */
@Singleton
class FavouritesRepositoryImpl @Inject constructor(
    private val api: SupabaseApi,
    private val authRepository: AuthRepository
) : FavouritesRepository {

    override suspend fun load(): RequestOutcome<List<FavouriteProduct>> {
        val token = authRepository.authorizedToken()
            ?: return RequestOutcome.Failure("Sign in to see your favourites.")
        return try {
            val response = api.getFavourites(
                apiKey = BuildConfig.SUPABASE_ANON_KEY,
                auth = "Bearer $token"
            )
            if (!response.isSuccessful) {
                return RequestOutcome.Failure("Could not load your favourites.")
            }
            val items = response.body().orEmpty().mapNotNull { row ->
                val product = row.products ?: return@mapNotNull null
                FavouriteProduct(
                    productId = row.product_id,
                    name = product.name.orEmpty(),
                    pricePaise = product.price_paise ?: 0L,
                    unitLabel = product.unit_label.orEmpty(),
                    imagePath = product.image_path,
                    mrpPaise = product.mrp_paise
                )
            }
            RequestOutcome.Success(items)
        } catch (e: Exception) {
            RequestOutcome.Failure("Could not load your favourites. Check your connection.")
        }
    }

    override suspend fun add(productId: String): RequestOutcome<Unit> =
        write(productId, add = true)

    override suspend fun remove(productId: String): RequestOutcome<Unit> =
        write(productId, add = false)

    private suspend fun write(productId: String, add: Boolean): RequestOutcome<Unit> {
        val userId = authRepository.session.value?.userId
            ?: return RequestOutcome.Failure("Sign in to save favourites.")
        val token = authRepository.authorizedToken()
            ?: return RequestOutcome.Failure("Your session expired. Please sign in again.")
        return try {
            val response: Response<Unit> = if (add) {
                api.addFavourite(
                    apiKey = BuildConfig.SUPABASE_ANON_KEY,
                    auth = "Bearer $token",
                    body = FavouriteRequest(customer_id = userId, product_id = productId)
                )
            } else {
                api.removeFavourite(
                    apiKey = BuildConfig.SUPABASE_ANON_KEY,
                    auth = "Bearer $token",
                    productId = productId
                )
            }
            if (response.isSuccessful) RequestOutcome.Success(Unit)
            else RequestOutcome.Failure("Could not update the favourite (HTTP ${response.code()}).")
        } catch (e: Exception) {
            RequestOutcome.Failure("Could not update the favourite. Check your connection.")
        }
    }
}
