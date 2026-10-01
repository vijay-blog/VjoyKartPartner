package com.daily.nexamartpartner.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumbersTest {

    @Test
    fun `normalizes every indian format to ten digits`() {
        listOf(
            "9959095202",
            " 9959095202 ",
            "+919959095202",
            "+91 99590 95202",
            "+91-99590-95202",
            "919959095202",
            "09959095202",
            "00919959095202",
            "(+91) 9959095202"
        ).forEach { input ->
            assertEquals(input, "9959095202", PhoneNumbers.normalizeIndianMobileOrNull(input))
        }
    }

    @Test
    fun `never produces a malformed provider number`() {
        val normalized = PhoneNumbers.normalizeIndianMobileOrNull("+919959095202")
        assertEquals(10, normalized?.length)
        assertFalse(normalized!!.startsWith("91"))
    }

    @Test
    fun `rejects invalid numbers`() {
        listOf(null, "", "12345", "1234567890", "5959095202", "99590952020", "abcdefghij")
            .forEach { assertNull(PhoneNumbers.normalizeIndianMobileOrNull(it)) }
    }

    @Test
    fun `keeps a valid ten digit number that starts with 91`() {
        assertEquals("9159095202", PhoneNumbers.normalizeIndianMobileOrNull("9159095202"))
    }

    @Test
    fun `validity helper matches normalization`() {
        assertTrue(PhoneNumbers.isValidIndianMobile("+91 99590 95202"))
        assertFalse(PhoneNumbers.isValidIndianMobile("12345"))
    }

    @Test
    fun `normalizeForInput keeps digits for partial entry`() {
        assertEquals("99590", PhoneNumbers.normalizeForInput("99590"))
        assertEquals("9959095202", PhoneNumbers.normalizeForInput("+91 99590 95202"))
    }
}
