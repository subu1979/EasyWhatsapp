package com.subu1979.imagesender

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subu1979.imagesender.ui.MainScreen
import com.subu1979.imagesender.ui.MainViewModel
import com.subu1979.imagesender.ui.theme.WhatsAppDirectTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            WhatsAppDirectTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                MainScreen(
                    state = state,
                    onNumberChange = viewModel::onNumberChange,
                    onCountryPickerVisibilityChange = viewModel::onCountryPickerVisibilityChange,
                    onCountrySelected = viewModel::onCountrySelected,
                    onImagePicked = viewModel::onImagePicked,
                    onCaptureStarted = viewModel::onCaptureStarted,
                    onCaptureResult = viewModel::onCaptureResult,
                    onCaptureUnavailable = viewModel::onCaptureUnavailable,
                    onContactsPermissionResult = viewModel::onContactsPermissionResult,
                    onAutoSendSettingsClick = viewModel::onAutoSendSettingsClick,
                    onOpenWhatsApp = viewModel::onOpenWhatsAppClick,
                    onOpenChat = viewModel::onOpenChatClick,
                    onConfirmRecipient = viewModel::onConfirmRecipient,
                    onConfirmationDismissed = viewModel::onConfirmationDismissed,
                    onAppChosen = viewModel::onAppChosen,
                    onAppChooserDismissed = viewModel::onAppChooserDismissed,
                    onMessageShown = viewModel::onMessageShown
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // WhatsApp may have been installed or removed while the app was in the background.
        viewModel.refreshInstalledApps()
        // Back from WhatsApp: the temporary recipient contact has done its job.
        viewModel.onSendFinished()
    }
}
