package com.subu1979.imagesender

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subu1979.imagesender.lab.LabScreen
import com.subu1979.imagesender.lab.LabViewModel
import com.subu1979.imagesender.ui.MainScreen
import com.subu1979.imagesender.ui.MainViewModel
import com.subu1979.imagesender.ui.theme.WhatsAppDirectTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val labViewModel: LabViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            WhatsAppDirectTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val labState by labViewModel.uiState.collectAsStateWithLifecycle()
                var showLab by remember { mutableStateOf(false) }

                if (showLab) {
                    BackHandler { showLab = false }
                    LabScreen(
                        state = labState,
                        recipient = state.recipientDigits,
                        hasImage = state.hasImage,
                        onMessageChange = labViewModel::onMessageChange,
                        onPayloadChange = labViewModel::onPayloadChange,
                        onTargetChange = labViewModel::onTargetChange,
                        onRun = { variant ->
                            labViewModel.runVariant(variant, state.recipientDigits, state.imageUri)
                        },
                        onObservation = labViewModel::recordObservation,
                        onDiscardPending = labViewModel::discardPending,
                        onCopyLog = labViewModel::copyLog,
                        onClearLog = labViewModel::clearLog,
                        onBack = { showLab = false }
                    )
                } else {
                    MainScreen(
                        state = state,
                        onNumberChange = viewModel::onNumberChange,
                        onCountryPickerVisibilityChange = viewModel::onCountryPickerVisibilityChange,
                        onCountrySelected = viewModel::onCountrySelected,
                        onImagePicked = viewModel::onImagePicked,
                        onCaptureStarted = viewModel::onCaptureStarted,
                        onCaptureResult = viewModel::onCaptureResult,
                        onCaptureUnavailable = viewModel::onCaptureUnavailable,
                        onOpenWhatsApp = viewModel::onOpenWhatsAppClick,
                        onOpenChat = viewModel::onOpenChatClick,
                        onConfirmRecipient = viewModel::onConfirmRecipient,
                        onConfirmationDismissed = viewModel::onConfirmationDismissed,
                        onAppChosen = viewModel::onAppChosen,
                        onAppChooserDismissed = viewModel::onAppChooserDismissed,
                        onMessageShown = viewModel::onMessageShown,
                        onMessageTextChange = viewModel::onMessageChange,
                        onOpenLab = {
                            labViewModel.refreshTargets(state.installedApps)
                            showLab = true
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // WhatsApp may have been installed or removed while the app was in the background.
        viewModel.refreshInstalledApps()
    }
}
