package com.nexamart.backend.util;

import com.nexamart.backend.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Single source of truth for mobile-number normalization.
 *
 * <p>Every stored/compared phone number in this application is the 10-digit Indian national
 * format (for example {@code 9959095202}). Inputs such as {@code +91 99590 95202},
 * {@code 0091-9959095202}, {@code 919959095202} and {@code 09959095202} all normalize to the
 * same value, which prevents duplicate accounts and malformed provider requests.
 */
public final class PhoneNumbers {

  public static final String INVALID_MESSAGE = "Please enter a valid 10-digit Indian mobile number.";

  private PhoneNumbers() {
  }

  /** Returns the 10-digit national number, or {@code null} when the value is not a valid Indian mobile. */
  public static String normalizeIndianMobileOrNull(String raw) {
    if (raw == null) {
      return null;
    }
    String digits = raw.replaceAll("[^0-9]", "");
    if (digits.length() > 10) {
      if (digits.startsWith("0091")) {
        digits = digits.substring(4);
      } else if (digits.startsWith("91")) {
        digits = digits.substring(2);
      } else if (digits.startsWith("0")) {
        digits = digits.substring(1);
      }
    }
    while (digits.length() > 10 && digits.startsWith("0")) {
      digits = digits.substring(1);
    }
    return digits.matches("[6-9]\\d{9}") ? digits : null;
  }

  /** Normalizes the value or fails with HTTP 400 — never HTTP 500. */
  public static String requireIndianMobile(String raw) {
    String normalized = normalizeIndianMobileOrNull(raw);
    if (normalized == null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, INVALID_MESSAGE);
    }
    return normalized;
  }

  /**
   * Best-effort normalization used by lookup flows that also accept email/username.
   * Returns the trimmed input when it is not a valid Indian mobile so that the caller
   * simply finds no match instead of throwing.
   */
  public static String normalizeForLookup(String raw) {
    String normalized = normalizeIndianMobileOrNull(raw);
    return normalized != null ? normalized : (raw == null ? "" : raw.trim());
  }

  /** {@code 9959095202} -> {@code +919959095202}. */
  public static String toE164India(String raw) {
    return "+91" + requireIndianMobile(raw);
  }

  /** Log-safe representation: {@code 9959095202} -> {@code ******5202}. */
  public static String mask(String raw) {
    String digits = raw == null ? "" : raw.replaceAll("[^0-9]", "");
    if (digits.length() < 4) {
      return "******";
    }
    return "******" + digits.substring(digits.length() - 4);
  }
}
