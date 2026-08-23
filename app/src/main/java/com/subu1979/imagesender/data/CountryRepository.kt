package com.subu1979.imagesender.data

import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale

/**
 * Builds the full country list from libphonenumber's supported regions, so the selector always
 * matches the metadata actually used for validation and normalisation (FR-01).
 */
object CountryRepository {

    const val DEFAULT_ISO2: String = "IN"

    private const val FLAG_OFFSET = 0x1F1E6
    private const val ASCII_A = 'A'.code

    fun loadCountries(locale: Locale = Locale.getDefault()): List<Country> {
        val util = PhoneNumberUtil.getInstance()
        return util.supportedRegions
            .mapNotNull { iso2 -> toCountry(util, iso2, locale) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    fun defaultCountry(countries: List<Country>): Country? =
        countries.firstOrNull { it.iso2 == DEFAULT_ISO2 } ?: countries.firstOrNull()

    /** Matches on country name, ISO alpha-2, ISO alpha-3 or dialing code (with or without '+'). */
    fun filter(countries: List<Country>, query: String): List<Country> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return countries
        val needle = trimmed.removePrefix("+").lowercase(Locale.ROOT)
        return countries.filter { country ->
            country.name.lowercase(Locale.ROOT).contains(needle) ||
                country.iso2.lowercase(Locale.ROOT).startsWith(needle) ||
                country.iso3.lowercase(Locale.ROOT).startsWith(needle) ||
                country.dialCode.toString().startsWith(needle)
        }
    }

    private fun toCountry(util: PhoneNumberUtil, iso2: String, locale: Locale): Country? {
        val dialCode = util.getCountryCodeForRegion(iso2)
        if (dialCode == 0) return null
        val regionLocale = Locale.Builder().setRegion(iso2).build()
        val name = regionLocale.getDisplayCountry(locale).ifBlank { iso2 }
        val iso3 = runCatching { regionLocale.isO3Country }.getOrDefault("")
        return Country(
            iso2 = iso2,
            iso3 = iso3,
            name = name,
            dialCode = dialCode,
            flag = flagEmoji(iso2)
        )
    }

    private fun flagEmoji(iso2: String): String {
        if (iso2.length != 2) return ""
        val upper = iso2.uppercase(Locale.ROOT)
        val first = FLAG_OFFSET + (upper[0].code - ASCII_A)
        val second = FLAG_OFFSET + (upper[1].code - ASCII_A)
        return String(Character.toChars(first)) + String(Character.toChars(second))
    }
}
