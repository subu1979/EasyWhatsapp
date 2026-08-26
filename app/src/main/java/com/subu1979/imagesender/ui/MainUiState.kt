package com.subu1979.imagesender.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.StringRes
import com.subu1979.imagesender.data.Country
import com.subu1979.imagesender.share.WhatsAppApp

/** What the user asked for, held while the WhatsApp / WhatsApp Business chooser is showing. */
enum class PendingAction { SHARE_IMAGE, OPEN_CHAT }

data class MainUiState(
    val countries: List<Country> = emptyList(),
    val selectedCountry: Country? = null,
    val nationalNumber: String = "",
    val numberIsValid: Boolean = false,
    /** Normalised recipient without '+', empty until the number validates. */
    val recipientDigits: String = "",
    /** Optional text; click-to-chat can pre-fill it for an unsaved number. */
    val messageText: String = "",
    val showNumberError: Boolean = false,
    val imageUri: Uri? = null,
    /** Destination handed to the camera app while a capture is in flight. */
    val pendingCaptureUri: Uri? = null,
    val preview: Bitmap? = null,
    val isPreviewLoading: Boolean = false,
    val showCountryPicker: Boolean = false,
    val appChooserFor: PendingAction? = null,
    /** Set while the "recipient may not be on WhatsApp" warning is showing. */
    val confirmationFor: PendingAction? = null,
    /** International number shown inside that warning, e.g. "+91 98765 43210". */
    val confirmationNumber: String = "",
    val installedApps: List<WhatsAppApp> = emptyList(),
    @param:StringRes val message: Int? = null
) {
    val hasImage: Boolean get() = imageUri != null
}
