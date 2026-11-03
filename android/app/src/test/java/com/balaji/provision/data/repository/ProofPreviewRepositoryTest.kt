package com.balaji.provision.data.repository

import com.balaji.provision.BuildConfig
import com.balaji.provision.data.auth.AuthRepository
import com.balaji.provision.data.network.OrderProofResponse
import com.balaji.provision.data.network.SignedProofUrlRequest
import com.balaji.provision.data.network.SignedProofUrlResponse
import com.balaji.provision.data.network.SupabaseApi
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*
import retrofit2.Response

class ProofPreviewRepositoryTest {
    private val api: SupabaseApi = mock()
    private val auth: AuthRepository = mock()
    private val repository = RequestsRepositoryImpl(api, auth)
    private val relative = "/object/sign/order-proofs/user-1/proof.png?token=synthetic"

    private suspend fun proof(path: String? = "user-1/proof.png") {
        whenever(auth.authorizedToken()).thenReturn("local-token")
        whenever(api.getOrderProofs(any(), any(), any(), any())).thenReturn(
            Response.success(listOf(OrderProofResponse("proof-1", "order-1", "dispatch", "image", path)))
        )
    }

    @Test fun `preview uses an authorized signed file URL rather than the image kind`() = runTest {
        proof()
        whenever(api.signOrderProof(any(), any(), any(), any(), any())).thenReturn(Response.success(SignedProofUrlResponse(relative)))
        val result = repository.proofUrl("order-1", "dispatch")
        assertEquals(RequestOutcome.Success(BuildConfig.SUPABASE_URL.trimEnd('/') + "/storage/v1" + relative), result)
        verify(api).signOrderProof(any(), eq("Bearer local-token"), eq("application/json"), eq("user-1/proof.png"), eq(SignedProofUrlRequest(60)))
    }

    @Test fun `signed URL expiry is serialized even when default values are omitted`() {
        assertEquals("{\"expiresIn\":60}", Json { encodeDefaults = false }.encodeToString(SignedProofUrlRequest(60)))
    }

    @Test fun `signed out user never requests a preview`() = runTest {
        whenever(auth.authorizedToken()).thenReturn(null)
        assertTrue(repository.proofUrl("order-1", "dispatch") is RequestOutcome.Failure)
        verifyNoInteractions(api)
    }

    @Test fun `unknown proof stage does not request storage access`() = runTest {
        assertTrue(repository.proofUrl("order-1", "unknown") is RequestOutcome.Failure)
        verifyNoInteractions(api)
    }

    @Test fun `missing proof path cannot be treated as a link`() = runTest {
        proof(null)
        assertTrue(repository.proofUrl("order-1", "dispatch") is RequestOutcome.Failure)
        verify(api, never()).signOrderProof(any(), any(), any(), any(), any())
    }

    @Test fun `storage denial has no public URL fallback`() = runTest {
        proof()
        whenever(api.signOrderProof(any(), any(), any(), any(), any())).thenReturn(Response.error(403, "{}".toResponseBody()))
        assertTrue(repository.proofUrl("order-1", "dispatch") is RequestOutcome.Failure)
    }

    @Test fun `unexpected external signed URL is rejected`() = runTest {
        proof()
        whenever(api.signOrderProof(any(), any(), any(), any(), any())).thenReturn(Response.success(SignedProofUrlResponse("https://example.invalid/proof")))
        assertTrue(repository.proofUrl("order-1", "dispatch") is RequestOutcome.Failure)
    }

    @Test fun `network interruption produces a retryable preview error`() = runTest {
        proof()
        whenever(api.signOrderProof(any(), any(), any(), any(), any())).thenAnswer { throw IOException("synthetic interruption") }
        val result = repository.proofUrl("order-1", "dispatch")
        assertTrue(result is RequestOutcome.Failure)
        assertTrue((result as RequestOutcome.Failure).message.contains("connection", ignoreCase = true))
    }

    @Test fun `cancelling preview propagates coroutine cancellation`() = runTest {
        proof()
        whenever(api.signOrderProof(any(), any(), any(), any(), any())).thenThrow(CancellationException("synthetic cancellation"))
        try { repository.proofUrl("order-1", "dispatch"); fail("Cancellation was swallowed") }
        catch (expected: CancellationException) { assertEquals("synthetic cancellation", expected.message) }
    }
}
