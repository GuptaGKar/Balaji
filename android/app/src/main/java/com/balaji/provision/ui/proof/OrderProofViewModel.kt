package com.balaji.provision.ui.proof

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.balaji.provision.data.repository.RequestOutcome
import com.balaji.provision.data.repository.RequestsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

/**
 * Dispatch / delivery proof. Staff must upload a photo or video before an order can go
 * out for delivery; the rider must upload one before it can be marked delivered. The
 * database enforces the same rule, so this view model only prepares the upload.
 */
@HiltViewModel
class OrderProofViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: RequestsRepository
) : ViewModel() {

    data class UiState(
        val orderId: String? = null,
        /** stage ("dispatch" | "delivery") -> kind ("image" | "video") */
        val proofs: Map<String, String> = emptyMap(),
        val loading: Boolean = false,
        val uploadingStage: String? = null,
        val message: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun load(orderId: String) {
        if (_state.value.orderId == orderId && _state.value.loading) return
        _state.value = _state.value.copy(orderId = orderId, loading = true)
        viewModelScope.launch {
            when (val result = repository.loadProofs(orderId)) {
                is RequestOutcome.Success -> _state.value = _state.value.copy(
                    loading = false,
                    proofs = result.value
                )
                is RequestOutcome.Failure -> _state.value = _state.value.copy(
                    loading = false,
                    message = result.message
                )
            }
        }
    }

    fun upload(
        orderId: String,
        stage: String,
        uri: Uri,
        onDone: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        if (_state.value.uploadingStage != null) return
        _state.value = _state.value.copy(uploadingStage = stage)
        viewModelScope.launch {
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            val kind = if (mimeType.startsWith("video/")) "video" else "image"

            val temp = withContext(Dispatchers.IO) {
                runCatching {
                    val file = File(context.cacheDir, "proof-${UUID.randomUUID()}")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    } ?: return@runCatching null
                    file
                }.getOrNull()
            }

            if (temp == null || temp.length() == 0L) {
                temp?.delete()
                finish(stage, "Could not read the selected file.", onDone)
                return@launch
            }
            if (temp.length() > MAX_PROOF_BYTES) {
                temp.delete()
                finish(stage, "The file is larger than 50 MB.", onDone)
                return@launch
            }

            val result = when (val upload = repository.uploadProof(temp, mimeType)) {
                is RequestOutcome.Failure -> upload
                is RequestOutcome.Success ->
                    repository.attachProof(orderId, stage, kind, upload.value)
            }
            temp.delete()

            when (result) {
                is RequestOutcome.Success -> {
                    _state.value = _state.value.copy(
                        uploadingStage = null,
                        proofs = _state.value.proofs + (stage to kind),
                        message = "Proof uploaded."
                    )
                    onDone(true, "Proof uploaded.")
                }
                is RequestOutcome.Failure -> finish(stage, result.message, onDone)
            }
        }
    }

    private fun finish(stage: String, message: String, onDone: (Boolean, String) -> Unit) {
        _state.value = _state.value.copy(uploadingStage = null, message = message)
        onDone(false, message)
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private companion object {
        const val MAX_PROOF_BYTES = 50L * 1024 * 1024
    }
}
