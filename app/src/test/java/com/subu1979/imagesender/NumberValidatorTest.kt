package com.subu1979.imagesender

import com.subu1979.imagesender.data.Country
import com.subu1979.imagesender.data.CountryRepository
import com.subu1979.imagesender.domain.NumberValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberValidatorTest {

    private val validator = NumberValidator()
    private val countries = CountryRepository.loadCountries()
    private val india = countries.first { it.iso2 == "IN" }

    @Test
    fun `default country is India with dialing code 91`() {
        val default = CountryRepository.defaultCountry(countries)
        assertEquals("IN", default?.iso2)
        assertEquals(91, default?.dialCode)
    }

    @Test
    fun `country list covers all supported regions`() {
        assertTrue(countries.size > 200)
        assertTrue(countries.all { it.dialCode > 0 && it.flag.isNotEmpty() })
    }

    @Test
    fun `search matches name, iso codes and dialing code`() {
        assertTrue(CountryRepository.filter(countries, "india").any { it.iso2 == "IN" })
        assertTrue(CountryRepository.filter(countries, "in").any { it.iso2 == "IN" })
        assertTrue(CountryRepository.filter(countries, "ind").any { it.iso2 == "IN" })
        assertTrue(CountryRepository.filter(countries, "+91").any { it.iso2 == "IN" })
    }

    @Test
    fun `valid indian number normalises to E164`() {
        val result = validator.validate(india, "9876543210")
        assertTrue(result is NumberValidator.Result.Valid)
        result as NumberValidator.Result.Valid
        assertEquals("+919876543210", result.e164)
        assertEquals("919876543210", result.digits)
    }

    @Test
    fun `spaces and dashes are ignored`() {
        val result = validator.validate(india, "98765 43210")
        assertTrue(result is NumberValidator.Result.Valid)
    }

    @Test
    fun `too short number is invalid`() {
        assertEquals(NumberValidator.Result.Invalid, validator.validate(india, "12345"))
    }

    @Test
    fun `empty input is not an error`() {
        assertEquals(NumberValidator.Result.Empty, validator.validate(india, ""))
    }

    @Test
    fun `no country selected is invalid`() {
        val none: Country? = null
        assertEquals(NumberValidator.Result.Invalid, validator.validate(none, "9876543210"))
    }
}
