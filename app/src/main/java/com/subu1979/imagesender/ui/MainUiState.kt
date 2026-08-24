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
    val showNumberError: Boolean = false,
    val imageUri: Uri? = null,
    /** Destination handed to the camera app while a capture is in flight. */
    val pendingCaptureUri: Uri? = null,
    val preview: Bitmap? = null,
    val isPreviewLoading: Boolean = false,
    /** True while the temporary contact is being made visible to WhatsApp. */
    val isPreparingRecipient: Boolean = false,
    /** Set when the UI should ask for Contacts access before continuing this action. */
    val permissionRequestFor: PendingAction? = null,
    val showCountryPicker: Boolean = false,
    val appChooserFor: PendingAction? = null,
    /** Set while the "recipient may not be on WhatsApp" warning is showing. */
    val confirmationFor: PendingAction? = null,
    /** International number shown inside that warning, e.g. "+91 98765 43210". */
    val confirmationNumber: String = "",
    val installedApps: List<WhatsAppApp> = emptyList(),
    /** Whether the Accessibility service that finishes the send inside WhatsApp is switched on. */
    val autoSendEnabled: Boolean = false,
    @param:StringRes val message: Int? = null
) {
    val hasImage: Boolean get() = imageUri != null
}
