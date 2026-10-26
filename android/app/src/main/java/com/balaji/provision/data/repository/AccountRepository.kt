package com.balaji.provision.data.repository

import com.balaji.provision.BuildConfig
import com.balaji.provision.data.auth.AuthRepository
import com.balaji.provision.data.network.AddressResponse
import com.balaji.provision.data.network.DeleteAccountRequest
import com.balaji.provision.data.network.DeleteAddressRequest
import com.balaji.provision.data.network.ProfileResponse
import com.balaji.provision.data.network.SaveMyAddressRequest
import com.balaji.provision.data.network.SetDefaultAddressRequest
import com.balaji.provision.data.network.SupabaseApi
import com.balaji.provision.data.network.UpdateMyProfileRequest
import com.balaji.provision.domain.model.AddressDraft
import com.balaji.provision.domain.model.CustomerProfile
import com.balaji.provision.domain.model.SavedAddress
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AccountResult<out T> {
    data class Success<T>(val value: T) : AccountResult<T>
    data class Failure(val message: String) : AccountResult<Nothing>
}

interface AccountRepository {
    suspend fun loadProfile(): AccountResult<CustomerProfile>
    suspend fun updateDisplayName(displayName: String): AccountResult<CustomerProfile>
    suspend fun loadAddresses(): AccountResult<List<SavedAddress>>
    suspend fun saveAddress(draft: AddressDraft, addressId: String? = null): AccountResult<SavedAddress>
    suspend fun setDefaultAddress(addressId: String): AccountResult<Unit>
    suspend fun deleteAddress(addressId: String): AccountResult<Unit>
    suspend fun deleteAccount(confirmation: String): AccountResult<Unit>
}

@Module
@InstallIn(SingletonComponent::class)
object AccountModule {
    @Provides
    @Singleton
    fun provideAccountRepository(
        api: SupabaseApi,
        authRepository: AuthRepository
    ): AccountRepository = AccountRepositoryImpl(api, authRepository)
}

@Singleton
class AccountRepositoryImpl @Inject constructor(
    private val api: SupabaseApi,
    private val authRepository: AuthRepository
) : AccountRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun loadProfile(): AccountResult<CustomerProfile> {
        val session = signedInSession() ?: return signedOut()
        return try {
            val response = api.getProfile(
                apiKey = BuildConfig.SUPABASE_ANON_KEY,
                auth = "Bearer ${session.token}",
                id = "eq.${session.userId}",
                select = "display_name,phone,role,is_active,must_change_password"
            )
            if (!response.isSuccessful) {
                return AccountResult.Failure(response.errorMessage("Could not load your profile."))
            }
            val profile = response.body()?.firstOrNull()
                ?: return AccountResult.Failure("Your profile could not be found.")
            AccountResult.Success(profile.toDomain())
        } catch (_: Exception) {
            AccountResult.Failure("Could not reach the store. Check your connection and try again.")
        }
    }

    override suspend fun updateDisplayName(displayName: String): AccountResult<CustomerProfile> {
        val name = displayName.trim()
        if (name.isBlank()) {
            return AccountResult.Failure("Enter your name.")
        }
        val session = signedInSession() ?: return signedOut()
        return try {
            val response = api.updateMyProfile(
                apiKey = BuildConfig.SUPABASE_ANON_KEY,
                auth = "Bearer ${session.token}",
                body = UpdateMyProfileRequest(p_display_name = name)
            )
            if (!response.isSuccessful) {
                return AccountResult.Failure(response.errorMessage("Could not update your profile."))
            }
            val profile = response.body()
            if (profile?.error != null) {
                return AccountResult.Failure(profile.message ?: "Could not update your profile.")
            }
            if (profile == null) {
                return AccountResult.Failure("Could not update your profile.")
            }
            AccountResult.Success(profile.toDomain())
        } catch (_: Exception) {
            AccountResult.Failure("Could not reach the store. Check your connection and try again.")
        }
    }

    override suspend fun loadAddresses(): AccountResult<List<SavedAddress>> {
        val session = signedInSession() ?: return signedOut()
        return try {
            val response = api.getAddresses(
                apiKey = BuildConfig.SUPABASE_ANON_KEY,
                auth = "Bearer ${session.token}",
                customerId = "eq.${session.userId}",
                select = "id,customer_id,label,recipient_name,phone,address_lines,landmark,area,city,state,postal_code,is_default,lat,lng",
                order = "is_default.desc,label.asc"
            )
            if (!response.isSuccessful) {
                return AccountResult.Failure(response.errorMessage("Could not load your saved addresses."))
            }
            AccountResult.Success(response.body().orEmpty().map(AddressResponse::toDomain))
        } catch (_: Exception) {
            AccountResult.Failure("Could not reach the store. Check your connection and try again.")
        }
    }

    override suspend fun saveAddress(
        draft: AddressDraft,
        addressId: String?
    ): AccountResult<SavedAddress> {
        val validationError = draft.validate()
        if (validationError != null) {
            return AccountResult.Failure(validationError)
        }
        val phone = draft.normalizedPhone()
            ?: return AccountResult.Failure("Enter a valid 10-digit mobile number.")
        val session = signedInSession() ?: return signedOut()
        return try {
            val response = api.saveMyAddress(
                apiKey = BuildConfig.SUPABASE_ANON_KEY,
                auth = "Bearer ${session.token}",
                body = SaveMyAddressRequest(
                    p_id = addressId,
                    p_label = draft.label.trim().ifBlank { "Home" },
                    p_recipient_name = draft.recipientName.trim(),
                    p_phone = phone,
                    p_address_lines = draft.addressLines.trim(),
                    p_landmark = draft.landmark.trim().ifBlank { null },
                    p_area = draft.area.trim().ifBlank { null },
                    p_city = draft.city.trim(),
                    p_state = draft.state.trim(),
                    p_postal_code = draft.postalCode.trim(),
                    p_is_default = draft.isDefault,
                    p_lat = draft.lat,
                    p_lng = draft.lng
                )
            )
            if (!response.isSuccessful) {
                return AccountResult.Failure(response.errorMessage("Could not save this address."))
            }
            val body = response.body()
            if (body?.error != null) {
                return AccountResult.Failure(body.message ?: "Could not save this address.")
            }
            val address = body?.address
                ?: return AccountResult.Failure("The address was not saved. Please try again.")
            AccountResult.Success(address.toDomain())
        } catch (_: Exception) {
            AccountResult.Failure("Could not reach the store. Check your connection and try again.")
        }
    }

    override suspend fun setDefaultAddress(addressId: String): AccountResult<Unit> {
        val session = signedInSession() ?: return signedOut()
        return try {
            val response = api.setDefaultAddress(
                apiKey = BuildConfig.SUPABASE_ANON_KEY,
                auth = "Bearer ${session.token}",
                body = SetDefaultAddressRequest(p_id = addressId)
            )
            if (!response.isSuccessful) {
                return AccountResult.Failure(response.errorMessage("Could not set the default address."))
            }
            val body = response.body()
            if (body?.success == true && body.error == null) {
                AccountResult.Success(Unit)
            } else {
                AccountResult.Failure(body?.message ?: "Could not set the default address.")
            }
        } catch (_: Exception) {
            AccountResult.Failure("Could not reach the store. Check your connection and try again.")
        }
    }

    override suspend fun deleteAddress(addressId: String): AccountResult<Unit> {
        val session = signedInSession() ?: return signedOut()
        return try {
            val response = api.deleteAddress(
                apiKey = BuildConfig.SUPABASE_ANON_KEY,
                auth = "Bearer ${session.token}",
                body = DeleteAddressRequest(p_id = addressId)
            )
            if (!response.isSuccessful) {
                return AccountResult.Failure(response.errorMessage("Could not delete this address."))
            }
            val body = response.body()
            if (body?.success == true && body.error == null) {
                AccountResult.Success(Unit)
            } else {
                AccountResult.Failure(body?.message ?: "Could not delete this address.")
            }
        } catch (_: Exception) {
            AccountResult.Failure("Could not reach the store. Check your connection and try again.")
        }
    }

    override suspend fun deleteAccount(confirmation: String): AccountResult<Unit> {
        if (!confirmation.trim().equals("DELETE", ignoreCase = true)) {
            return AccountResult.Failure("Type DELETE to confirm account deletion.")
        }
        val session = signedInSession() ?: return signedOut()
        return try {
            val response = api.deleteMyAccount(
                apiKey = BuildConfig.SUPABASE_ANON_KEY,
                auth = "Bearer ${session.token}",
                body = DeleteAccountRequest(p_confirmation = "DELETE")
            )
            if (!response.isSuccessful) {
                return AccountResult.Failure(
                    response.errorMessage("Could not delete your account. Please try again.")
                )
            }
            val body = response.body()
            if (body?.error != null) {
                return AccountResult.Failure(body.message ?: "Could not delete your account.")
            }
            if (body?.success == true) {
                AccountResult.Success(Unit)
            } else {
                AccountResult.Failure(body?.message ?: "Could not delete your account.")
            }
        } catch (_: Exception) {
            AccountResult.Failure("Could not reach the store. Check your connection and try again.")
        }
    }

    private suspend fun signedInSession(): Session? {
        val userId = authRepository.session.value?.userId?.takeIf(String::isNotBlank)
            ?: return null
        val token = authRepository.authorizedToken() ?: return null
        return Session(userId, token)
    }

    private fun signedOut() = AccountResult.Failure("Sign in to manage your profile and addresses.")

    private fun Response<*>.errorMessage(fallback: String): String {
        val raw = errorBody()?.string().orEmpty()
        val parsed = runCatching { json.decodeFromString<AccountApiError>(raw) }.getOrNull()
        return parsed?.error_description
            ?: parsed?.message
            ?: parsed?.msg
            ?: parsed?.error
            ?: fallback
    }

    private data class Session(val userId: String, val token: String)
}

private fun AddressResponse.toDomain(): SavedAddress = SavedAddress(
    id = id,
    label = label.ifBlank { "Home" },
    recipientName = recipient_name,
    phone = phone,
    addressLines = address_lines,
    landmark = landmark?.takeIf(String::isNotBlank),
    area = area?.takeIf(String::isNotBlank),
    city = city.orEmpty(),
    state = state.orEmpty(),
    postalCode = postal_code,
    isDefault = is_default,
    lat = lat,
    lng = lng
)

private fun ProfileResponse.toDomain(): CustomerProfile = CustomerProfile(
    displayName = display_name?.takeIf(String::isNotBlank),
    phone = phone?.takeIf(String::isNotBlank),
    role = role ?: "customer",
    isActive = is_active ?: true,
    mustChangePassword = must_change_password ?: false
)

@Serializable
private data class AccountApiError(
    val error: String? = null,
    val message: String? = null,
    val msg: String? = null,
    val error_description: String? = null
)
