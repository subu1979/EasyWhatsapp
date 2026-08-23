package com.subu1979.imagesender.domain

import com.subu1979.imagesender.data.Country
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil

/**
 * Combines the selected dialing code with the entered national number and normalises it to E.164
 * before any sharing flow is launched (FR-01).
 */
class NumberValidator(
    private val util: PhoneNumberUtil = PhoneNumberUtil.getInstance()
) {

    sealed interface Result {
        /** Nothing typed yet: not an error while the user is still editing. */
        data object Empty : Result

        data object Invalid : Result

        /**
         * @param e164 normalised number with '+', e.g. "+919876543210".
         * @param digits normalised number without '+', the form wa.me links expect.
         */
        data class Valid(val e164: String, val digits: String) : Result
    }

    fun validate(country: Country?, nationalNumber: String): Result {
        if (country == null) return Result.Invalid
        val typed = nationalNumber.filter { it.isDigit() }
        if (typed.isEmpty()) return Result.Empty

        return try {
            val parsed = util.parse("+${country.dialCode}$typed", country.iso2)
            if (!util.isValidNumber(parsed)) {
                Result.Invalid
            } else {
                val e164 = util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
                Result.Valid(e164 = e164, digits = e164.removePrefix("+"))
            }
        } catch (_: NumberParseException) {
            Result.Invalid
        }
    }
}
