package com.balaji.provision.ui.account

import com.balaji.provision.data.auth.AuthRepository
import com.balaji.provision.data.auth.AuthSession
import com.balaji.provision.data.repository.AccountRepository
import com.balaji.provision.data.repository.AccountResult
import com.balaji.provision.domain.model.AddressDraft
import com.balaji.provision.domain.model.CustomerProfile
import com.balaji.provision.domain.model.SavedAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val auth: AuthRepository = mock()
    private lateinit var repository: FakeAccountRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeAccountRepository()
        whenever(auth.session).thenReturn(
            MutableStateFlow(
                AuthSession(
                    userId = "user-1",
                    accessToken = "access",
                    refreshToken = "refresh",
                    expiresAtMillis = Long.MAX_VALUE
                )
            )
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `refresh exposes profile and saved addresses`() = runTest(dispatcher) {
        val viewModel = AccountViewModel(repository, auth)
        advanceUntilIdle()

        assertEquals("Asha", viewModel.state.value.profile?.displayName)
        assertEquals(1, viewModel.state.value.addresses.size)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `save address returns the address and marks it selected in state`() = runTest(dispatcher) {
        val viewModel = AccountViewModel(repository, auth)
        advanceUntilIdle()
        var saved: SavedAddress? = null
        var error: String? = "not-called"

        viewModel.saveAddress(validDraft(), null) { success, address, message ->
            if (success) saved = address
            error = message
        }
        advanceUntilIdle()

        assertEquals("address-2", saved?.id)
        assertNull(error)
        assertEquals(2, viewModel.state.value.addresses.size)
        assertEquals(true, viewModel.state.value.addresses.first { it.id == "address-2" }.isDefault)
        assertEquals(false, viewModel.state.value.addresses.first { it.id == "address-1" }.isDefault)
    }

    @Test
    fun `delete account clears state and reports success`() = runTest(dispatcher) {
        val viewModel = AccountViewModel(repository, auth)
        advanceUntilIdle()
        var success = false
        var message: String? = "not-called"

        viewModel.deleteAccount("DELETE") { result, resultMessage ->
            success = result
            message = resultMessage
        }
        advanceUntilIdle()

        assertEquals(true, success)
        assertNull(message)
        assertNull(viewModel.state.value.profile)
        assertFalse(viewModel.state.value.isDeleting)
    }

    @Test
    fun `update display name updates the profile on success`() = runTest(dispatcher) {
        val viewModel = AccountViewModel(repository, auth)
        advanceUntilIdle()

        viewModel.updateDisplayName("Asha Rao") { _, _ -> }
        advanceUntilIdle()

        assertEquals("Asha Rao", viewModel.state.value.profile?.displayName)
    }

    private fun validDraft() = AddressDraft(
        label = "Work",
        recipientName = "Asha",
        phone = "9876543210",
        addressLines = "12, MG Road",
        city = "Pune",
        state = "Maharashtra",
        postalCode = "411005",
        isDefault = true
    )

    private class FakeAccountRepository : AccountRepository {
        private val profile = CustomerProfile(
            displayName = "Asha",
            phone = "9876543210",
            role = "customer"
        )
        private val addresses = mutableListOf(
            SavedAddress(
                id = "address-1",
                label = "Home",
                recipientName = "Asha",
                phone = "9876543210",
                addressLines = "12, MG Road",
                landmark = null,
                area = null,
                city = "Pune",
                state = "Maharashtra",
                postalCode = "411005",
                isDefault = true
            )
        )

        override suspend fun loadProfile(): AccountResult<CustomerProfile> =
            AccountResult.Success(profile)

        override suspend fun updateDisplayName(displayName: String): AccountResult<CustomerProfile> {
            val updated = profile.copy(displayName = displayName)
            return AccountResult.Success(updated)
        }

        override suspend fun loadAddresses(): AccountResult<List<SavedAddress>> =
            AccountResult.Success(addresses.toList())

        override suspend fun saveAddress(
            draft: AddressDraft,
            addressId: String?
        ): AccountResult<SavedAddress> {
            val saved = SavedAddress(
                id = addressId ?: "address-2",
                label = draft.label,
                recipientName = draft.recipientName,
                phone = draft.normalizedPhone() ?: draft.phone,
                addressLines = draft.addressLines,
                landmark = draft.landmark.ifBlank { null },
                area = draft.area.ifBlank { null },
                city = draft.city,
                state = draft.state,
                postalCode = draft.postalCode,
                isDefault = draft.isDefault
            )
            addresses.removeAll { it.id == saved.id }
            if (saved.isDefault) {
                addresses.replaceAll { it.copy(isDefault = false) }
            }
            addresses.add(saved)
            return AccountResult.Success(saved)
        }

        override suspend fun setDefaultAddress(addressId: String): AccountResult<Unit> {
            addresses.replaceAll { it.copy(isDefault = it.id == addressId) }
            return AccountResult.Success(Unit)
        }

        override suspend fun deleteAddress(addressId: String): AccountResult<Unit> {
            addresses.removeAll { it.id == addressId }
            return AccountResult.Success(Unit)
        }

        override suspend fun deleteAccount(confirmation: String): AccountResult<Unit> =
            if (confirmation.trim().equals("DELETE", ignoreCase = true)) {
                AccountResult.Success(Unit)
            } else {
                AccountResult.Failure("Type DELETE to confirm account deletion.")
            }
    }
}
