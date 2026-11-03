package com.balaji.provision.data.repository

import com.balaji.provision.data.auth.AuthRepository
import com.balaji.provision.data.auth.AuthSession
import com.balaji.provision.data.network.SupabaseApi
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*
import retrofit2.Response

class ProofUploadRepositoryTest {
    private val api: SupabaseApi = mock()
    private val auth: AuthRepository = mock()
    private val repository = RequestsRepositoryImpl(api, auth)

    private suspend fun signIn() {
        whenever(auth.session).thenReturn(MutableStateFlow(AuthSession("rider-1", "local-token", "local-refresh", Long.MAX_VALUE)))
        whenever(auth.authorizedToken()).thenReturn("local-token")
    }

    @Test fun `repeated proof uploads create fresh objects without overwrite permission`() = runTest {
        signIn()
        whenever(api.uploadOrderProof(any(), any(), any(), any(), any(), any())).thenReturn(Response.success(Unit))
        val file = File.createTempFile("qa-proof", ".png")
        try {
            file.writeBytes(byteArrayOf(1, 2, 3))
            assertTrue(repository.uploadProof(file, "image/png") is RequestOutcome.Success)
            assertTrue(repository.uploadProof(file, "image/png") is RequestOutcome.Success)
            val overwrite = argumentCaptor<String>()
            val paths = argumentCaptor<String>()
            verify(api, times(2)).uploadOrderProof(any(), any(), any(), overwrite.capture(), paths.capture(), any())
            assertEquals(listOf("false", "false"), overwrite.allValues)
            assertEquals(2, paths.allValues.distinct().size)
            assertTrue(paths.allValues.all { it.startsWith("rider-1/proof/") && it.endsWith(".png") })
        } finally { file.delete() }
    }

    @Test fun `storage RLS rejection is reported as failure rather than a saved path`() = runTest {
        signIn()
        whenever(api.uploadOrderProof(any(), any(), any(), any(), any(), any())).thenReturn(
            Response.error(400, "{\"message\":\"new row violates row-level security policy\"}".toResponseBody())
        )
        val file = File.createTempFile("qa-proof", ".png")
        try {
            file.writeBytes(byteArrayOf(1, 2, 3))
            assertTrue(repository.uploadProof(file, "image/png") is RequestOutcome.Failure)
        } finally { file.delete() }
    }
}
