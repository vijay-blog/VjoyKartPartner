package com.nexamart.backend.util;

import com.nexamart.backend.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PhoneNumbersTest {

  @Test
  void normalizesEveryIndianFormatToTenDigits() {
    assertEquals("9959095202", PhoneNumbers.requireIndianMobile("9959095202"));
    assertEquals("9959095202", PhoneNumbers.requireIndianMobile(" 9959095202 "));
    assertEquals("9959095202", PhoneNumbers.requireIndianMobile("+919959095202"));
    assertEquals("9959095202", PhoneNumbers.requireIndianMobile("+91 99590 95202"));
    assertEquals("9959095202", PhoneNumbers.requireIndianMobile("+91-99590-95202"));
    assertEquals("9959095202", PhoneNumbers.requireIndianMobile("919959095202"));
    assertEquals("9959095202", PhoneNumbers.requireIndianMobile("09959095202"));
    assertEquals("9959095202", PhoneNumbers.requireIndianMobile("00919959095202"));
    assertEquals("9959095202", PhoneNumbers.requireIndianMobile("(+91) 9959095202"));
  }

  @Test
  void neverProducesMalformedProviderInput() {
    String normalized = PhoneNumbers.requireIndianMobile("+919959095202");
    assertEquals(10, normalized.length());
    assertEquals("+919959095202", PhoneNumbers.toE164India("+919959095202"));
    assertEquals("+919959095202", PhoneNumbers.toE164India("9959095202"));
  }

  @Test
  void rejectsInvalidNumbersWithBadRequest() {
    for (String bad : new String[]{null, "", "12345", "1234567890", "5959095202", "99590952020", "abcdefghij"}) {
      ApiException e = assertThrows(ApiException.class, () -> PhoneNumbers.requireIndianMobile(bad));
      assertEquals(HttpStatus.BAD_REQUEST, e.status());
    }
    assertNull(PhoneNumbers.normalizeIndianMobileOrNull("12345"));
  }

  @Test
  void doesNotStripPrefixFromAValidTenDigitNumberStartingWith91() {
    assertEquals("9159095202", PhoneNumbers.requireIndianMobile("9159095202"));
  }

  @Test
  void masksAllButTheLastFourDigits() {
    assertEquals("******5202", PhoneNumbers.mask("9959095202"));
    assertEquals("******5202", PhoneNumbers.mask("+91 99590 95202"));
    assertEquals("******", PhoneNumbers.mask(null));
  }
}
