package com.subu1979.imagesender.data

/**
 * A country/territory supported by the phone-number metadata (FR-01).
 *
 * @param iso2 ISO 3166-1 alpha-2 code, e.g. "IN".
 * @param iso3 ISO 3166-1 alpha-3 code, e.g. "IND". Empty when the platform has no mapping.
 * @param name Localised display name, e.g. "India".
 * @param dialCode International dialing code without the leading '+', e.g. 91.
 * @param flag Flag emoji built from the ISO code, so no image assets are shipped.
 */
data class Country(
    val iso2: String,
    val iso3: String,
    val name: String,
    val dialCode: Int,
    val flag: String
) {
    val dialCodeText: String get() = "+$dialCode"

    /** Display format required by the PRD: flag + country name + dialing code. */
    val displayLabel: String get() = "$flag  $name ($dialCodeText)"
}
