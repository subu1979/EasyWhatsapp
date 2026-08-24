package com.subu1979.imagesender.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.subu1979.imagesender.R
import com.subu1979.imagesender.share.ContactPermission
import com.subu1979.imagesender.share.ImageStore
import com.subu1979.imagesender.share.WhatsAppApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: MainUiState,
    onNumberChange: (String) -> Unit,
    onCountryPickerVisibilityChange: (Boolean) -> Unit,
    onCountrySelected: (String) -> Unit,
    onImagePicked: (android.net.Uri?) -> Unit,
    onCaptureStarted: (android.net.Uri) -> Unit,
    onCaptureResult: (Boolean) -> Unit,
    onCaptureUnavailable: () -> Unit,
    onContactsPermissionResult: (Boolean) -> Unit,
    onAutoSendSettingsClick: () -> Unit,
    onOpenWhatsApp: () -> Unit,
    onOpenChat: () -> Unit,
    onConfirmRecipient: () -> Unit,
    onConfirmationDismissed: () -> Unit,
    onAppChosen: (WhatsAppApp) -> Unit,
    onAppChooserDismissed: () -> Unit,
    onMessageShown: () -> Unit
) {
    val messageText = state.message?.let { stringResource(it) }

    val context = LocalContext.current

    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = onImagePicked
    )
    val launchPicker = {
        pickImage.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    val requestContacts = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { grants -> onContactsPermissionResult(grants.values.all { it }) }
    )
    LaunchedEffect(state.permissionRequestFor) {
        if (state.permissionRequestFor != null) requestContacts.launch(ContactPermission.REQUIRED)
    }

    val takePicture = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = onCaptureResult
    )
    val launchCamera = {
        val destination = ImageStore.createCaptureUri(context)
        if (destination == null) {
            onCaptureUnavailable()
        } else {
            onCaptureStarted(destination)
            // No camera app on the device throws instead of returning a result.
            runCatching { takePicture.launch(destination) }
                .onFailure { onCaptureUnavailable() }
            Unit
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.headline),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            RecipientRow(
                state = state,
                onNumberChange = onNumberChange,
                onCountryClick = { onCountryPickerVisibilityChange(true) }
            )

            if (state.showNumberError && !state.numberIsValid) {
                Text(
                    text = stringResource(R.string.error_invalid_number),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            ImageSection(state = state, onPick = launchPicker, onCapture = launchCamera)

            Button(
                onClick = onOpenWhatsApp,
                enabled = !state.isPreparingRecipient,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
            ) {
                Text(
                    stringResource(
                        if (state.isPreparingRecipient) {
                            R.string.action_preparing_recipient
                        } else {
                            R.string.action_open_whatsapp
                        }
                    )
                )
            }

            TextButton(onClick = onOpenChat, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_open_chat_only))
            }

            AutoSendCard(
                enabled = state.autoSendEnabled,
                onManage = onAutoSendSettingsClick
            )

            Text(
                text = stringResource(
                    if (state.autoSendEnabled) R.string.share_hint_auto else R.string.share_hint
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = stringResource(R.string.privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))
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
        MessageDialog(message = messageText, onDismiss = onMessageShown)
    }

    if (state.confirmationFor != null) {
        ConfirmRecipientDialog(
            number = state.confirmationNumber,
            onConfirm = onConfirmRecipient,
            onDismiss = onConfirmationDismissed
        )
    }

    if (state.appChooserFor != null) {
        AppChooserDialog(
            apps = state.installedApps,
            onChoose = onAppChosen,
            onDismiss = onAppChooserDismissed
        )
    }
}

/** Status of the optional Accessibility helper, with a shortcut to the system switch. */
@Composable
private fun AutoSendCard(enabled: Boolean, onManage: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.auto_send_title),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = stringResource(
                        if (enabled) R.string.auto_send_on else R.string.auto_send_off
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onManage) {
                Text(
                    stringResource(
                        if (enabled) R.string.auto_send_manage else R.string.auto_send_enable
                    )
                )
            }
        }
    }
}

@Composable
private fun RecipientRow(
    state: MainUiState,
    onNumberChange: (String) -> Unit,
    onCountryClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(
            onClick = onCountryClick,
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
}

@Composable
private fun ImageSection(state: MainUiState, onPick: () -> Unit, onCapture: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                state.isPreviewLoading -> CircularProgressIndicator()

                state.preview != null -> Image(
                    bitmap = state.preview.asImageBitmap(),
                    contentDescription = stringResource(R.string.content_desc_selected_image),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .clip(RoundedCornerShape(12.dp))
                )

                else -> Text(
                    text = stringResource(R.string.error_no_image),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(onClick = onPick, modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        if (state.hasImage) R.string.action_change_image else R.string.action_select_image
                    ),
                    maxLines = 1
                )
            }
            OutlinedButton(onClick = onCapture, modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.action_take_photo), maxLines = 1)
            }
        }
    }
}

/**
 * Shown once per session before the first hand-off. WhatsApp registration cannot be checked from
 * a third-party app, so the user is told what WhatsApp will do rather than given a false verdict.
 */
@Composable
private fun ConfirmRecipientDialog(
    number: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_check_recipient)) },
        text = { Text(stringResource(R.string.dialog_confirm_recipient, number)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_continue)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/** All errors from PRD section 12 are surfaced as a blocking alert the user must acknowledge. */
@Composable
private fun MessageDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_notice)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
        }
    )
}

@Composable
private fun AppChooserDialog(
    apps: List<WhatsAppApp>,
    onChoose: (WhatsAppApp) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_app)) },
        text = {
            Column {
                apps.forEach { app ->
                    TextButton(
                        onClick = { onChoose(app) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(app.label)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
