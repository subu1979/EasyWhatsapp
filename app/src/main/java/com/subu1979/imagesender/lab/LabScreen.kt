package com.subu1979.imagesender.lab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.subu1979.imagesender.share.WhatsAppApp

/**
 * A bench for the question the product cannot answer by reasoning: what does this WhatsApp build do
 * with each intent permutation? Every variant is launched for real, and the user records what
 * appeared. Nothing here automates WhatsApp — that would confuse the measurement.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabScreen(
    state: LabUiState,
    recipient: String,
    hasImage: Boolean,
    onMessageChange: (String) -> Unit,
    onPayloadChange: (Payload) -> Unit,
    onTargetChange: (WhatsAppApp) -> Unit,
    onRun: (Variant) -> Unit,
    onObservation: (Observation) -> Unit,
    onDiscardPending: () -> Unit,
    onCopyLog: () -> Unit,
    onClearLog: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Intent lab") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (recipient.isBlank()) {
                    "Enter a number on the main screen first."
                } else {
                    "Recipient +$recipient · image ${if (hasImage) "selected" else "not selected"}"
                },
                style = MaterialTheme.typography.bodyMedium
            )

            OutlinedTextField(
                value = state.message,
                onValueChange = onMessageChange,
                label = { Text("Message") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("Payload", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Payload.entries.forEach { payload ->
                    FilterChip(
                        selected = state.payload == payload,
                        onClick = { onPayloadChange(payload) },
                        label = { Text(payload.label) }
                    )
                }
            }

            if (state.availableTargets.size > 1) {
                Text("Target app", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.availableTargets.forEach { target ->
                        FilterChip(
                            selected = state.target == target,
                            onClick = { onTargetChange(target) },
                            label = { Text(target.label) }
                        )
                    }
                }
            }

            HorizontalDivider()

            Variant.entries.forEach { variant ->
                VariantCard(
                    variant = variant,
                    enabled = recipient.isNotBlank() &&
                        variant.accepts(state.payload) &&
                        (state.payload == Payload.TEXT || hasImage),
                    onRun = { onRun(variant) }
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onCopyLog, modifier = Modifier.weight(1f)) {
                    Text("Copy results")
                }
                OutlinedButton(onClick = onClearLog, modifier = Modifier.weight(1f)) {
                    Text("Clear")
                }
            }

            if (state.log.isEmpty()) {
                Text(
                    text = "No results yet. Run a variant, watch what WhatsApp does, come back and record it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.log.asReversed().forEach { entry ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = entry,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.height(24.dp)) {}
        }
    }

    state.pending?.let { pending ->
        ObservationDialog(
            variantLabel = pending.variant.label,
            resolved = pending.resolvedActivity,
            launch = pending.launch,
            onObservation = onObservation,
            onDismiss = onDiscardPending
        )
    }
}

@Composable
private fun VariantCard(variant: Variant, enabled: Boolean, onRun: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(variant.label, style = MaterialTheme.typography.titleSmall)
            Text(
                text = variant.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRun, enabled = enabled) { Text("Run") }
                if (!variant.supportsImage) {
                    AssistChip(onClick = {}, label = { Text("text only") })
                }
            }
        }
    }
}

/** Asked immediately after the user returns, while the screen they saw is still fresh. */
@Composable
private fun ObservationDialog(
    variantLabel: String,
    resolved: String,
    launch: String,
    onObservation: (Observation) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What did WhatsApp show?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(variantLabel, style = MaterialTheme.typography.labelLarge)
                Text(
                    text = "resolved: $resolved\nlaunch: $launch",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
                Observation.entries.forEach { observation ->
                    OutlinedButton(
                        onClick = { onObservation(observation) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(observation.label)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Discard") } }
    )
}
