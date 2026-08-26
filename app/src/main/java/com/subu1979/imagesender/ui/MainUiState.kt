package com.subu1979.imagesender.ui

import androidx.annotation.StringRes
import com.subu1979.imagesender.data.Country
import com.subu1979.imagesender.auto.SendMode
import com.subu1979.imagesender.share.WhatsAppApp

data class MainUiState(
    val countries: List<Country> = emptyList(),
    val selectedCountry: Country? = null,
    val nationalNumber: String = "",
    val numberIsValid: Boolean = false,
    val showNumberError: Boolean = false,
    val showCountryPicker: Boolean = false,
    /** Set while the user chooses between WhatsApp and WhatsApp Business. */
    val showAppChooser: Boolean = false,
    val installedApps: List<WhatsAppApp> = emptyList(),
    val mode: SendMode = SendMode.MANUAL,
    /** Whether the Accessibility service Auto mode needs is switched on in system settings. */
    val serviceEnabled: Boolean = false,
    /** What the service did last, newest last. Empty until Auto mode runs. */
    val autoLog: List<String> = emptyList(),
    @param:StringRes val message: Int? = null
)
