package com.subu1979.imagesender.lab

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.subu1979.imagesender.share.WhatsAppApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What the user saw after a variant was launched. The device answers; the user reports. */
enum class Observation(val label: String, val code: String) {
    DIRECT_CHAT("Landed in the recipient's chat", "RECIPIENT_PRESELECTED"),
    SEND_TO_PICKER("\"Send to…\" picker appeared", "SEND_TO_PICKER"),
    CHAT_NO_ATTACHMENT("Chat opened, nothing attached", "CHAT_NO_ATTACHMENT"),
    WHATSAPP_ERROR("WhatsApp showed an error", "WHATSAPP_ERROR"),
    NOTHING("WhatsApp did not open", "NO_LAUNCH")
}

data class LabUiState(
    val message: String = "Test message",
    val payload: Payload = Payload.TEXT_AND_IMAGE,
    val target: WhatsAppApp = WhatsAppApp.STANDARD,
    val availableTargets: List<WhatsAppApp> = emptyList(),
    val pending: LabResult? = null,
    val log: List<String> = emptyList()
)

class LabViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(LabUiState())
    val uiState: StateFlow<LabUiState> = _uiState.asStateFlow()

    private val context: Context get() = getApplication()

    fun onMessageChange(value: String) = _uiState.update { it.copy(message = value) }

    fun onPayloadChange(payload: Payload) = _uiState.update { it.copy(payload = payload) }

    fun onTargetChange(target: WhatsAppApp) = _uiState.update { it.copy(target = target) }

    fun refreshTargets(targets: List<WhatsAppApp>) {
        _uiState.update { state ->
            state.copy(
                availableTargets = targets,
                target = targets.firstOrNull { it == state.target } ?: targets.firstOrNull() ?: state.target
            )
        }
    }

    fun runVariant(variant: Variant, digits: String, image: Uri?) {
        val state = _uiState.value
        val result = IntentLab.run(
            context = context,
            variant = variant,
            target = state.target,
            payload = state.payload,
            digits = digits,
            text = state.message,
            image = image
        )
        _uiState.update { it.copy(pending = result) }
    }

    /** Files the observation against the pending result and appends the finished record. */
    fun recordObservation(observation: Observation) {
        val result = _uiState.value.pending ?: return
        _uiState.update { it.copy(pending = null, log = it.log + format(result, observation)) }
    }

    fun discardPending() = _uiState.update { it.copy(pending = null) }

    fun clearLog() = _uiState.update { it.copy(log = emptyList()) }

    fun copyLog() {
        val text = _uiState.value.log.joinToString("\n\n").ifBlank { return }
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("whatsapp-intent-lab", text))
    }

    private fun format(result: LabResult, observation: Observation): String = buildString {
        appendLine("TEST: ${result.variant.id}")
        appendLine("TARGET: ${result.target.packageName}")
        appendLine("PAYLOAD: ${result.payload.name}")
        appendLine("ACTION: ${result.action}")
        appendLine("TYPE: ${result.mimeType ?: "-"}")
        appendLine("COMPONENT: ${result.component ?: "-"}")
        appendLine("EXTRAS: ${result.extras.joinToString(", ")}")
        appendLine("RESOLVED: ${result.resolvedActivity}")
        appendLine("LAUNCH: ${result.launch}")
        append("OBSERVED: ${observation.code}")
    }
}
