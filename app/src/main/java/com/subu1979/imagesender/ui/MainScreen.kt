package com.subu1979.imagesender.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.subu1979.imagesender.R
import com.subu1979.imagesender.share.WhatsAppApp

/**
 * One screen, one job: turn a typed number into an open WhatsApp chat. Anything the user wants to
 * send — photo, camera shot, text — is picked inside WhatsApp, which already has those tools and,
 * unlike this app, can attach them to a chat with an unsaved number.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: MainUiState,
    onNumberChange: (String) -> Unit,
    onCountryPickerVisibilityChange: (Boolean) -> Unit,
    onCountrySelected: (String) -> Unit,
    onInitiate: () -> Unit,
    onAppChosen: (WhatsAppApp) -> Unit,
    onAppChooserDismissed: () -> Unit,
    onMessageShown: () -> Unit
) {
    val messageText = state.message?.let { stringResource(it) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.headline),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onCountryPickerVisibilityChange(true) },
                    modifier = Modifier
                        .width(132.dp)
                        .heightIn(min = 56.dp)
                ) {
                    val country = state.selectedCountry
                    Text(
                        text = if (country == null) "…" else "${country.flag} ${country.dialCodeText}",
                        maxLines = 1
                    )
                }

                OutlinedTextField(
                    value = state.nationalNumber,
                    onValueChange = onNumberChange,
                    label = { Text(stringResource(R.string.label_mobile_number)) },
                    placeholder = { Text(stringResource(R.string.hint_mobile_number)) },
                    singleLine = true,
                    isError = state.showNumberError && !state.numberIsValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.weight(1f)
                )
            }

            if (state.showNumberError && !state.numberIsValid) {
                Text(
                    text = stringResource(R.string.error_invalid_number),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                onClick = onInitiate,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
            ) {
                Text(stringResource(R.string.action_initiate))
            }

            Text(
                text = stringResource(R.string.usage_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = stringResource(R.string.privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (state.showCountryPicker) {
        CountryPickerSheet(
            countries = state.countries,
            selectedIso2 = state.selectedCountry?.iso2,
            onSelect = onCountrySelected,
            onDismiss = { onCountryPickerVisibilityChange(false) }
        )
    }

    if (messageText != null) {
        AlertDialog(
            onDismissRequest = onMessageShown,
            title = { Text(stringResource(R.string.dialog_title_notice)) },
            text = { Text(messageText) },
            confirmButton = {
                TextButton(onClick = onMessageShown) { Text(stringResource(R.string.ok)) }
            }
        )
    }

    if (state.showAppChooser) {
        AlertDialog(
            onDismissRequest = onAppChooserDismissed,
            title = { Text(stringResource(R.string.choose_app)) },
            text = {
                Column {
                    state.installedApps.forEach { app ->
                        TextButton(
                            onClick = { onAppChosen(app) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(app.label)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onAppChooserDismissed) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
