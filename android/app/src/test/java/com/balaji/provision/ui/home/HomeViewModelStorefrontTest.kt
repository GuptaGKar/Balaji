package com.balaji.provision.ui.home

import com.balaji.provision.data.auth.AuthRepository
import com.balaji.provision.data.network.StoreSettingsResponse
import com.balaji.provision.data.network.StorefrontRequest
import com.balaji.provision.data.network.StorefrontSectionResponse
import com.balaji.provision.data.network.SupabaseApi
import com.balaji.provision.data.repository.AccountRepository
import com.balaji.provision.data.repository.AccountResult
import com.balaji.provision.data.repository.StorefrontRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import retrofit2.Response

/**
 * The composed shelf has to survive the store-settings load.
 *
 * `HomeViewModel.init` fires `loadStoreInfo()` and the storefront read together, and
 * `loadStoreInfo()` used to finish by assigning a brand-new `HomeUiState(...)` instead of
 * `copy(...)`. Because that constructor omits `sections`, whatever the storefront read had just
 * put there was reset to empty - so a section published in the dashboard never reached the
 * screen even though the server was returning it. The same clobber happens every time the
 * customer taps "Try again" on the store-info error state, which calls `loadStoreInfo()`
 * directly.
 *
 * These tests call `loadStoreInfo()` explicitly rather than racing the two coroutines: the order
 * they finish in depends on how many round trips each takes, and a fix that makes the order
 * irrelevant is the point.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelStorefrontTest {

    private val dispatcher = StandardTestDispatcher()
    private val api: SupabaseApi = mock()
    private val auth: AuthRepository = mock()
    private val accountRepository: AccountRepository = mock()
    private lateinit var storefrontRepository: StorefrontRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        storefrontRepository = StorefrontRepository(api, auth)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val sectionRow = StorefrontSectionResponse(
        id = "d43f2f00-7fcf-4d7f-bb7b-b5e1de47b5aa",
        title = "Diwali Specials",
        subtitle = null,
        section_type = "products",
        updated_at = "2026-11-05T19:26:01.932497+00:00",
        item_ids = listOf("ccce2012-6626-4a5e-a734-0ca0a38a1425", "9f57e730-c55e-4608-855c-d1b0dfcc8977")
    )

    private suspend fun stub(settings: List<StoreSettingsResponse>, storefront: Response<List<StorefrontSectionResponse>>) {
        whenever(auth.authorizedToken()).thenReturn("jwt")
        whenever(accountRepository.loadAddresses()).thenReturn(AccountResult.Success(emptyList()))
        whenever(api.getStorefront(anyString(), anyString(), anyString(), any<StorefrontRequest>()))
            .thenReturn(storefront)
        whenever(api.getStoreSettings(anyString(), anyString(), anyString(), anyInt()))
            .thenReturn(Response.success(settings))
    }

    @Test
    fun `loadStoreInfo keeps the composed sections it did not load`() = runTest(dispatcher) {
        stub(listOf(StoreSettingsResponse(store_open = true)), Response.success(listOf(sectionRow)))

        val viewModel = HomeViewModel(api, accountRepository, storefrontRepository)
        advanceUntilIdle()
        assertEquals("the first read must land at all", 1, viewModel.state.value.sections.size)

        // This is exactly what the store-info retry button does, and what the settings load
        // effectively does when it finishes after the storefront read.
        viewModel.loadStoreInfo()
        advanceUntilIdle()

        assertEquals(
            "store settings replaced the whole state and reset sections to empty",
            1,
            viewModel.state.value.sections.size
        )
        assertEquals("Diwali Specials", viewModel.state.value.sections.single().title)
        assertEquals(
            listOf("ccce2012-6626-4a5e-a734-0ca0a38a1425", "9f57e730-c55e-4608-855c-d1b0dfcc8977"),
            viewModel.state.value.sections.single().itemIds
        )
    }

    @Test
    fun `loadStoreInfo still publishes its own fields while keeping sections`() = runTest(dispatcher) {
        stub(listOf(StoreSettingsResponse(store_open = true)), Response.success(listOf(sectionRow)))
        val viewModel = HomeViewModel(api, accountRepository, storefrontRepository)
        advanceUntilIdle()

        viewModel.loadStoreInfo()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(1, state.sections.size)
        assertEquals(true, state.storeOpen)
        assertEquals(false, state.isLoading)
        assertEquals(null, state.error)
    }

    @Test
    fun `a settings failure does not clear the composed sections either`() = runTest(dispatcher) {
        stub(listOf(StoreSettingsResponse(store_open = true)), Response.success(listOf(sectionRow)))
        val viewModel = HomeViewModel(api, accountRepository, storefrontRepository)
        advanceUntilIdle()

        // Second call answers with nothing, which is the "could not load" branch.
        whenever(api.getStoreSettings(anyString(), anyString(), anyString(), anyInt()))
            .thenReturn(Response.success(emptyList()))
        viewModel.loadStoreInfo()
        advanceUntilIdle()

        assertEquals("an error banner is not a reason to blank the shelf", 1, viewModel.state.value.sections.size)
        assertTrue("and the error must still be reported", viewModel.state.value.error != null)
    }

    @Test
    fun `an un-migrated database yields the built-in layout, not an error`() = runTest(dispatcher) {
        stub(
            listOf(StoreSettingsResponse(store_open = true)),
            Response.error(400, "".toResponseBody("application/json".toMediaType()))
        )

        val viewModel = HomeViewModel(api, accountRepository, storefrontRepository)
        advanceUntilIdle()
        viewModel.loadStoreInfo()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("empty sections is the fallback signal", emptyList<Any>(), state.sections)
        assertEquals(true, state.storeOpen)
        assertEquals(null, state.error)
    }

    @Test
    fun `a categories page read is served to the categories view model`() = runTest(dispatcher) {
        // Same decode path, different page argument: the two pages compose independently, so a
        // section saved for categories must not be answered with the home layout.
        val categoriesRow = sectionRow.copy(id = "c1", title = "Festive aisles", section_type = "categories")
        stub(listOf(StoreSettingsResponse(store_open = true)), Response.success(listOf(categoriesRow)))

        val sections = storefrontRepository.sectionsFor(page = "categories", force = true)

        assertEquals(1, sections.size)
        assertEquals(
            com.balaji.provision.data.repository.SectionKind.Categories,
            sections.single().type
        )
        org.mockito.Mockito.verify(api)
            .getStorefront(anyString(), anyString(), anyString(), any<StorefrontRequest>())
    }
}
