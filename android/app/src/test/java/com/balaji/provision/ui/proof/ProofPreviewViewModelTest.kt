package com.balaji.provision.ui.proof

import android.content.Context
import com.balaji.provision.data.repository.RequestOutcome
import com.balaji.provision.data.repository.RequestsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class ProofPreviewViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository: RequestsRepository = mock()
    private val context: Context = mock()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun `each completed View action requests a fresh link`() = runTest(dispatcher) {
        whenever(repository.proofUrl("order-1", "dispatch")).thenReturn(RequestOutcome.Success("https://example.invalid/first"), RequestOutcome.Success("https://example.invalid/second"))
        val model = OrderProofViewModel(context, repository)
        val links = mutableListOf<String>()
        model.openProof("order-1", "dispatch", links::add); advanceUntilIdle()
        model.openProof("order-1", "dispatch", links::add); advanceUntilIdle()
        assertEquals(listOf("https://example.invalid/first", "https://example.invalid/second"), links)
        assertNull(model.state.value.openingStage)
    }

    @Test fun `denied preview shows an error and never opens a viewer`() = runTest(dispatcher) {
        whenever(repository.proofUrl(any(), any())).thenReturn(RequestOutcome.Failure("Proof unavailable"))
        val model = OrderProofViewModel(context, repository)
        var opened = false
        model.openProof("order-1", "delivery") { opened = true }; advanceUntilIdle()
        assertFalse(opened)
        assertEquals("Proof unavailable", model.state.value.message)
        assertNull(model.state.value.openingStage)
    }

    @Test fun `overlapping View taps share one in-flight request`() = runTest(dispatcher) {
        whenever(repository.proofUrl(any(), any())).thenReturn(RequestOutcome.Success("https://example.invalid/proof"))
        val model = OrderProofViewModel(context, repository)
        model.openProof("order-1", "dispatch") {}; model.openProof("order-1", "dispatch") {}
        assertEquals("dispatch", model.state.value.openingStage)
        advanceUntilIdle()
        verify(repository, times(1)).proofUrl("order-1", "dispatch")
        assertNull(model.state.value.openingStage)
    }
}
