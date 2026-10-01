package com.daily.nexamartpartner.core.util

/**
 * Single source of truth for mobile-number normalization on the client.
 *
 * Mirrors the backend `com.nexamart.backend.util.PhoneNumbers` so that the app and the API always
 * agree on the stored value: the 10-digit Indian national number (for example `9959095202`).
 * Inputs such as `+91 99590 95202`, `0091-9959095202`, `919959095202` and `09959095202` all
 * collapse to the same value, which prevents malformed numbers from ever reaching the OTP provider.
 */
object PhoneNumbers {

    private val VALID = Regex("^[6-9]\\d{9}$")

    /** Returns the 10-digit national number, or `null` when the value is not a valid Indian mobile. */
    fun normalizeIndianMobileOrNull(raw: String?): String? {
        if (raw == null) return null
        var digits = raw.filter(Char::isDigit)
        if (digits.length > 10) {
            digits = when {
                digits.startsWith("0091") -> digits.substring(4)
                digits.startsWith("91") -> digits.substring(2)
                digits.startsWith("0") -> digits.substring(1)
                else -> digits
            }
        }
        while (digits.length > 10 && digits.startsWith("0")) {
            digits = digits.substring(1)
        }
        return if (VALID.matches(digits)) digits else null
    }

    /** Best-effort normalization that keeps the digits even when they are not a valid mobile yet. */
    fun normalizeForInput(raw: String?): String =
        normalizeIndianMobileOrNull(raw) ?: raw?.filter(Char::isDigit).orEmpty()

    fun isValidIndianMobile(raw: String?): Boolean = normalizeIndianMobileOrNull(raw) != null
}
