package com.nexamart.backend.otp;

import com.nexamart.backend.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TwoFactorOtpSmsSenderTest {

  @Test
  void productionConstructorIsExplicitlyAutowiredForSpring() throws Exception {
    assertTrue(TwoFactorOtpSmsSender.class
        .getConstructor(AppProperties.class)
        .isAnnotationPresent(Autowired.class));
  }

  private AppProperties props;
  private HttpClient http;

  @BeforeEach
  void setUp() {
    props = new AppProperties();
    props.setOtpApiKey("test-api-key");
    props.setOtpBaseUrl("https://2factor.in");
    props.setOtpTimeoutSeconds(5);
    http = mock(HttpClient.class);
  }

  @SuppressWarnings("unchecked")
  private void respond(int status, String body) throws Exception {
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(status);
    when(response.body()).thenReturn(body);
    when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
  }

  private TwoFactorOtpSmsSender sender() {
    return new TwoFactorOtpSmsSender(props, http);
  }

  @Test
  void missingApiKeyIsReportedAsConfigurationProblem() {
    props.setOtpApiKey("");
    assertFalse(sender().isConfigured());
    OtpProviderException e = assertThrows(OtpProviderException.class, () -> sender().send("9959095202", "123456"));
    assertEquals(OtpProviderException.Reason.CONFIG_MISSING, e.reason());
    assertEquals(503, e.reason().status().value());
  }

  @Test
  void providerSuccessDoesNotThrow() throws Exception {
    respond(200, "{\"Status\":\"Success\",\"Details\":\"a0c1f8d2-0000-0000-0000-000000000000\"}");
    sender().send("9959095202", "123456");
  }

  @Test
  void requestUsesTenDigitNumberAndNeverDoublePrefixes() throws Exception {
    respond(200, "{\"Status\":\"Success\",\"Details\":\"ok\"}");
    org.mockito.ArgumentCaptor<HttpRequest> captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
    sender().send("9959095202", "123456");
    org.mockito.Mockito.verify(http).send(captor.capture(), any(HttpResponse.BodyHandler.class));
    String url = captor.getValue().uri().toString();
    assertEquals("https://2factor.in/API/V1/test-api-key/SMS/9959095202/123456", url);
    assertFalse(url.contains("+91+91"));
    assertFalse(url.contains("9199590952020"));
    assertFalse(url.contains("00919959095202"));
  }

  @Test
  void optionalTemplateIsAppendedWhenConfigured() throws Exception {
    props.setOtpTemplateName("VJOYKART OTP");
    respond(200, "{\"Status\":\"Success\",\"Details\":\"ok\"}");
    org.mockito.ArgumentCaptor<HttpRequest> captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
    sender().send("9959095202", "123456");
    org.mockito.Mockito.verify(http).send(captor.capture(), any(HttpResponse.BodyHandler.class));
    assertTrue(captor.getValue().uri().toString().endsWith("/VJOYKART+OTP"));
  }

  @Test
  void invalidApiKeyIsClassifiedAsAuthenticationFailure() throws Exception {
    respond(200, "{\"Status\":\"Error\",\"Details\":\"Invalid API Key\"}");
    OtpProviderException e = assertThrows(OtpProviderException.class, () -> sender().send("9959095202", "123456"));
    assertEquals(OtpProviderException.Reason.AUTH_FAILED, e.reason());
    assertEquals(502, e.reason().status().value());
  }

  @Test
  void http401IsClassifiedAsAuthenticationFailure() throws Exception {
    respond(401, "Unauthorized");
    assertEquals(OtpProviderException.Reason.AUTH_FAILED,
        assertThrows(OtpProviderException.class, () -> sender().send("9959095202", "123456")).reason());
  }

  @Test
  void invalidMobileNumberIsClassifiedAsBadRequest() throws Exception {
    respond(200, "{\"Status\":\"Error\",\"Details\":\"Invalid Mobile Number\"}");
    OtpProviderException e = assertThrows(OtpProviderException.class, () -> sender().send("9959095202", "123456"));
    assertEquals(OtpProviderException.Reason.INVALID_PHONE, e.reason());
    assertEquals(400, e.reason().status().value());
  }

  @Test
  void invalidTemplateIsClassifiedAsConfigurationProblem() throws Exception {
    respond(200, "{\"Status\":\"Error\",\"Details\":\"Invalid Template Name\"}");
    assertEquals(OtpProviderException.Reason.INVALID_TEMPLATE,
        assertThrows(OtpProviderException.class, () -> sender().send("9959095202", "123456")).reason());
  }

  @Test
  void insufficientBalanceIsClassified() throws Exception {
    respond(200, "{\"Status\":\"Error\",\"Details\":\"Insufficient Balance\"}");
    assertEquals(OtpProviderException.Reason.INSUFFICIENT_BALANCE,
        assertThrows(OtpProviderException.class, () -> sender().send("9959095202", "123456")).reason());
  }

  @Test
  void providerOutageIsClassifiedAsUnavailable() throws Exception {
    respond(503, "Service Unavailable");
    assertEquals(OtpProviderException.Reason.UNAVAILABLE,
        assertThrows(OtpProviderException.class, () -> sender().send("9959095202", "123456")).reason());
  }

  @Test
  void malformedResponseDoesNotCrashTheBackend() throws Exception {
    respond(200, "<html>gateway</html>");
    assertEquals(OtpProviderException.Reason.MALFORMED_RESPONSE,
        assertThrows(OtpProviderException.class, () -> sender().send("9959095202", "123456")).reason());

    respond(200, "");
    assertEquals(OtpProviderException.Reason.MALFORMED_RESPONSE,
        assertThrows(OtpProviderException.class, () -> sender().send("9959095202", "123456")).reason());
  }

  @Test
  @SuppressWarnings("unchecked")
  void timeoutIsClassifiedAsTimeout() throws Exception {
    when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new HttpTimeoutException("timed out"));
    OtpProviderException e = assertThrows(OtpProviderException.class, () -> sender().send("9959095202", "123456"));
    assertEquals(OtpProviderException.Reason.TIMEOUT, e.reason());
    assertEquals(504, e.reason().status().value());
  }

  @Test
  void errorDetailNeverLeaksTheApiKey() throws Exception {
    respond(200, "{\"Status\":\"Error\",\"Details\":\"Invalid API Key test-api-key\"}");
    OtpProviderException e = assertThrows(OtpProviderException.class, () -> sender().send("9959095202", "123456"));
    assertFalse(e.providerDetail().contains("test-api-key"));
  }
}
