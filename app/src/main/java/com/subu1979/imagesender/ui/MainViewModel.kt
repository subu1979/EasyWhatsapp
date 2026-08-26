package com.subu1979.imagesender.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.subu1979.imagesender.R
import com.subu1979.imagesender.data.CountryRepository
import com.subu1979.imagesender.domain.NumberValidator
import com.subu1979.imagesender.share.ShareResult
import com.subu1979.imagesender.share.WhatsAppApp
import com.subu1979.imagesender.share.WhatsAppShareManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val validator = NumberValidator()

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val context: Context get() = getApplication()

    init {
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

    fun onInitiateClick() {
        val state = _uiState.value
        if (validator.validate(state.selectedCountry, state.nationalNumber) !is NumberValidator.Result.Valid) {
            _uiState.update { it.copy(showNumberError = true, message = R.string.error_invalid_number) }
            return
        }

        val apps = WhatsAppShareManager.installedApps(context)
        _uiState.update { it.copy(installedApps = apps) }
        when (apps.size) {
            0 -> _uiState.update { it.copy(message = R.string.error_whatsapp_missing) }
            1 -> openChat(apps.first())
            else -> _uiState.update { it.copy(showAppChooser = true) }
        }
    }

    fun onAppChosen(app: WhatsAppApp) {
        _uiState.update { it.copy(showAppChooser = false) }
        openChat(app)
    }

    fun onAppChooserDismissed() {
        _uiState.update { it.copy(showAppChooser = false) }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }

    private fun openChat(app: WhatsAppApp) {
        val state = _uiState.value
        val number = validator.validate(state.selectedCountry, state.nationalNumber)
        if (number !is NumberValidator.Result.Valid) {
            _uiState.update { it.copy(showNumberError = true, message = R.string.error_invalid_number) }
            return
        }

        val result = WhatsAppShareManager.openChat(context, number.digits, app)
        val message = when (result) {
            ShareResult.Success -> null
            ShareResult.NotInstalled -> R.string.error_whatsapp_missing
            ShareResult.LaunchFailed -> R.string.error_launch_failed
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
