package com.subu1979.imagesender.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.subu1979.imagesender.R
import com.subu1979.imagesender.data.CountryRepository
import com.subu1979.imagesender.domain.NumberValidator
import com.subu1979.imagesender.share.ImageStore
import com.subu1979.imagesender.share.ShareResult
import com.subu1979.imagesender.share.WhatsAppApp
import com.subu1979.imagesender.share.WhatsAppShareManager
import com.google.i18n.phonenumbers.PhoneNumberUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val validator = NumberValidator()

    /** The unregistered-recipient warning is shown once per session, not before every send. */
    private var warningAcknowledged = false

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val context: Context get() = getApplication()

    init {
        ImageStore.clearCache(context)
        viewModelScope.launch {
            // Building the list touches metadata, so keep it off the first frame.
            val countries = withContext(Dispatchers.Default) { CountryRepository.loadCountries() }
            _uiState.update { state ->
                state.copy(
                    countries = countries,
                    selectedCountry = state.selectedCountry ?: CountryRepository.defaultCountry(countries)
                )
            }
            revalidate()
        }
        refreshInstalledApps()
    }

    /** WhatsApp may be installed or removed while the app sits in the background. */
    fun refreshInstalledApps() {
        _uiState.update { it.copy(installedApps = WhatsAppShareManager.installedApps(context)) }
    }

    fun onNumberChange(input: String) {
        val digits = input.filter { it.isDigit() }.take(MAX_NATIONAL_DIGITS)
        _uiState.update { it.copy(nationalNumber = digits, showNumberError = false) }
        revalidate()
    }

    fun onCountrySelected(iso2: String) {
        _uiState.update { state ->
            state.copy(
                selectedCountry = state.countries.firstOrNull { it.iso2 == iso2 } ?: state.selectedCountry,
                showCountryPicker = false,
                showNumberError = false
            )
        }
        revalidate()
    }

    fun onCountryPickerVisibilityChange(visible: Boolean) {
        _uiState.update { it.copy(showCountryPicker = visible) }
    }

    fun onImagePicked(uri: Uri?) {
        if (uri == null) return
        _uiState.update { it.copy(imageUri = uri, preview = null, isPreviewLoading = true) }
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) { ImageStore.loadPreview(context, uri) }
            if (bitmap == null) {
                _uiState.update {
                    it.copy(
                        imageUri = null,
                        preview = null,
                        isPreviewLoading = false,
                        message = R.string.error_image_unreadable
                    )
                }
            } else {
                _uiState.update { it.copy(preview = bitmap, isPreviewLoading = false) }
            }
        }
    }

    /** Called with the FileProvider destination just before the camera app is launched. */
    fun onCaptureStarted(uri: Uri) {
        _uiState.update { it.copy(pendingCaptureUri = uri) }
    }

    fun onCaptureResult(success: Boolean) {
        val uri = _uiState.value.pendingCaptureUri
        _uiState.update { it.copy(pendingCaptureUri = null) }
        if (success && uri != null) onImagePicked(uri)
    }

    fun onCaptureUnavailable() {
        _uiState.update { it.copy(pendingCaptureUri = null, message = R.string.error_no_camera) }
    }

    fun onOpenWhatsAppClick() = startAction(PendingAction.SHARE_IMAGE)

    fun onOpenChatClick() = startAction(PendingAction.OPEN_CHAT)

    fun onConfirmRecipient() {
        val action = _uiState.value.confirmationFor ?: return
        warningAcknowledged = true
        _uiState.update { it.copy(confirmationFor = null) }
        chooseTarget(action)
    }

    fun onConfirmationDismissed() {
        _uiState.update { it.copy(confirmationFor = null) }
    }

    fun onAppChosen(app: WhatsAppApp) {
        val action = _uiState.value.appChooserFor ?: return
        _uiState.update { it.copy(appChooserFor = null) }
        perform(action, app)
    }

    fun onAppChooserDismissed() {
        _uiState.update { it.copy(appChooserFor = null) }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }

    private fun startAction(action: PendingAction) {
        val state = _uiState.value
        val result = validator.validate(state.selectedCountry, state.nationalNumber)
        if (result !is NumberValidator.Result.Valid) {
            _uiState.update { it.copy(showNumberError = true, message = R.string.error_invalid_number) }
            return
        }
        if (action == PendingAction.SHARE_IMAGE && !state.hasImage) {
            _uiState.update { it.copy(message = R.string.error_no_image) }
            return
        }

        // Whether the recipient is registered on WhatsApp cannot be checked from a third-party
        // app, so warn once per session instead of pretending to know.
        if (!warningAcknowledged) {
            _uiState.update {
                it.copy(
                    confirmationFor = action,
                    confirmationNumber = formatForDisplay(result.e164)
                )
            }
            return
        }

        chooseTarget(action)
    }

    private fun chooseTarget(action: PendingAction) {
        val apps = WhatsAppShareManager.installedApps(context)
        _uiState.update { it.copy(installedApps = apps) }
        when (apps.size) {
            0 -> _uiState.update { it.copy(message = R.string.error_whatsapp_missing) }
            1 -> perform(action, apps.first())
            else -> _uiState.update { it.copy(appChooserFor = action) }
        }
    }

    /** Groups the E.164 digits so the user can proof-read the recipient before handing off. */
    private fun formatForDisplay(e164: String): String = runCatching {
        val util = PhoneNumberUtil.getInstance()
        util.format(util.parse(e164, null), PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
    }.getOrDefault(e164)

    private fun perform(action: PendingAction, app: WhatsAppApp) {
        val state = _uiState.value
        val number = validator.validate(state.selectedCountry, state.nationalNumber)
        if (number !is NumberValidator.Result.Valid) {
            _uiState.update { it.copy(showNumberError = true, message = R.string.error_invalid_number) }
            return
        }

        val result = when (action) {
            PendingAction.OPEN_CHAT -> WhatsAppShareManager.openChat(context, number.digits, app)
            PendingAction.SHARE_IMAGE -> shareImageWithFallback(state.imageUri, app)
        }
        reportShareResult(result)
    }

    /**
     * Forwards the picked URI first; if the target cannot be granted access, retries once with an
     * app-owned copy exposed through FileProvider.
     */
    private fun shareImageWithFallback(imageUri: Uri?, app: WhatsAppApp): ShareResult {
        val uri = imageUri ?: return ShareResult.LaunchFailed
        val direct = if (ImageStore.canRead(context, uri)) {
            WhatsAppShareManager.shareImage(context, uri, app)
        } else {
            ShareResult.UriNotGrantable
        }
        if (direct != ShareResult.UriNotGrantable) return direct

        val copy = ImageStore.copyToCache(context, uri) ?: return ShareResult.LaunchFailed
        return WhatsAppShareManager.shareImage(context, copy, app)
    }

    private fun reportShareResult(result: ShareResult) {
        val message = when (result) {
            ShareResult.Success -> null
            ShareResult.NotInstalled -> R.string.error_whatsapp_missing
            ShareResult.LaunchFailed, ShareResult.UriNotGrantable -> R.string.error_launch_failed
        }
        if (message != null) _uiState.update { it.copy(message = message) }
    }

    private fun revalidate() {
        val state = _uiState.value
        val valid = validator.validate(state.selectedCountry, state.nationalNumber) is NumberValidator.Result.Valid
        _uiState.update { it.copy(numberIsValid = valid) }
    }

    private companion object {
        const val MAX_NATIONAL_DIGITS = 15
    }
}
