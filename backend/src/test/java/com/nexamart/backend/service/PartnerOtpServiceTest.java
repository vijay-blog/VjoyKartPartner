package com.nexamart.backend.service;

import com.nexamart.backend.api.ApiModels.LoginResponse;
import com.nexamart.backend.api.ApiModels.OtpSendResponse;
import com.nexamart.backend.api.ApiModels.UserResponse;
import com.nexamart.backend.config.AppProperties;
import com.nexamart.backend.domain.AccountStatus;
import com.nexamart.backend.domain.OtpChallenge;
import com.nexamart.backend.domain.Role;
import com.nexamart.backend.domain.UserAccount;
import com.nexamart.backend.exception.ApiException;
import com.nexamart.backend.otp.OtpProviderException;
import com.nexamart.backend.otp.OtpSmsSender;
import com.nexamart.backend.repository.OtpChallengeRepository;
import com.nexamart.backend.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PartnerOtpServiceTest {

  private static final String PHONE = "9959095202";

  private UserAccountRepository users;
  private OtpChallengeRepository challenges;
  private AppProperties props;
  private AuthService auth;
  private OtpSmsSender sms;
  private PartnerOtpService service;

  @BeforeEach
  void setUp() {
    users = mock(UserAccountRepository.class);
    challenges = mock(OtpChallengeRepository.class);
    auth = mock(AuthService.class);
    sms = mock(OtpSmsSender.class);

    props = new AppProperties();
    props.setOtpApiKey("key");
    props.setOtpTtlSeconds(300);
    props.setOtpResendCooldownSeconds(30);
    props.setOtpMaxVerifyAttempts(5);
    props.setOtpMaxSendsPerHour(5);

    when(sms.isConfigured()).thenReturn(true);
    when(challenges.saveAndFlush(any(OtpChallenge.class))).thenAnswer(i -> i.getArgument(0));
    when(challenges.findTopByPhoneOrderByCreatedAtDesc(anyString())).thenReturn(Optional.empty());
    when(challenges.countByPhoneAndCreatedAtAfter(anyString(), any())).thenReturn(0L);
    when(auth.touch(any(UserAccount.class))).thenAnswer(i -> i.getArgument(0));
    when(auth.issueDeliveryPartnerSession(any(UserAccount.class)))
        .thenReturn(new LoginResponse("access", "refresh",
            new UserResponse(7L, "Partner", PHONE, "p@example.com", "DELIVERY_PARTNER")));

    service = new PartnerOtpService(new UserLookupService(users), challenges, props, auth, sms);
  }

  // ---------- helpers ----------

  private UserAccount user(long id, Role role, AccountStatus status) {
    UserAccount u = new UserAccount();
    setId(u, id);
    u.setName("Partner " + id);
    u.setPhone(PHONE);
    u.setRole(role);
    u.setStatus(status);
    return u;
  }

  private static void setId(UserAccount u, long id) {
    try {
      Field f = UserAccount.class.getDeclaredField("id");
      f.setAccessible(true);
      f.set(u, id);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private void registeredPartner() {
    when(users.findAllByPhoneOrderByIdAsc(PHONE))
        .thenReturn(List.of(user(1L, Role.DELIVERY_PARTNER, AccountStatus.ACTIVE)));
  }

  private OtpChallenge challengeFor(String otp, Instant expiresAt, int attempts, boolean verified) {
    OtpChallenge c = new OtpChallenge();
    c.setPhone(PHONE);
    c.setOtpHash(hash(PHONE, otp));
    c.setExpiresAt(expiresAt);
    c.setAttempts(attempts);
    c.setVerified(verified);
    return c;
  }

  private static String hash(String phone, String otp) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      return java.util.HexFormat.of().formatHex(md.digest((phone + ":" + otp).getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  // ---------- send ----------

  @Test
  void validRegisteredMobileSendsOtpAndPersistsHashedChallenge() {
    registeredPartner();

    OtpSendResponse response = service.send("+91 99590 95202");

    assertTrue(response.success());
    assertEquals("SMS", response.deliveryChannel());
    assertEquals(300, response.expiresInSeconds());
    assertEquals(null, response.devOtp());

    ArgumentCaptor<OtpChallenge> saved = ArgumentCaptor.forClass(OtpChallenge.class);
    verify(challenges).saveAndFlush(saved.capture());
    assertEquals(PHONE, saved.getValue().getPhone());
    assertEquals(64, saved.getValue().getOtpHash().length());
    assertTrue(saved.getValue().getExpiresAt().isAfter(Instant.now()));

    ArgumentCaptor<String> otp = ArgumentCaptor.forClass(String.class);
    verify(sms).send(eq(PHONE), otp.capture());
    assertTrue(otp.getValue().matches("\\d{6}"), "OTP must be 6 digits");
  }

  @Test
  void invalidMobileFailsWithBadRequestAndNeverCallsProvider() {
    ApiException e = assertThrows(ApiException.class, () -> service.send("12345"));
    assertEquals(HttpStatus.BAD_REQUEST, e.status());
    verify(sms, never()).send(anyString(), anyString());
  }

  @Test
  void unregisteredMobileReturnsNotFoundInsteadOfServerError() {
    when(users.findAllByPhoneOrderByIdAsc(PHONE)).thenReturn(List.of());

    ApiException e = assertThrows(ApiException.class, () -> service.send(PHONE));

    assertEquals(HttpStatus.NOT_FOUND, e.status());
    assertEquals("Delivery partner not registered. Please create an account.", e.getMessage());
  }

  /** Reproduces the production HTTP 500: two user rows sharing one mobile number. */
  @Test
  void duplicateAccountsForTheSameMobileResolveToTheDeliveryPartner() {
    List<UserAccount> rows = new ArrayList<>();
    rows.add(user(1L, Role.CUSTOMER, AccountStatus.ACTIVE));
    rows.add(user(2L, Role.DELIVERY_PARTNER, AccountStatus.ACTIVE));
    when(users.findAllByPhoneOrderByIdAsc(PHONE)).thenReturn(rows);

    OtpSendResponse response = service.send(PHONE);

    assertTrue(response.success());
    verify(sms).send(eq(PHONE), anyString());
  }

  @Test
  void customerOnlyAccountIsNotTreatedAsADeliveryPartner() {
    when(users.findAllByPhoneOrderByIdAsc(PHONE))
        .thenReturn(List.of(user(1L, Role.CUSTOMER, AccountStatus.ACTIVE)));

    ApiException e = assertThrows(ApiException.class, () -> service.send(PHONE));
    assertEquals(HttpStatus.NOT_FOUND, e.status());
  }

  @Test
  void suspendedPartnerIsForbiddenNotServerError() {
    when(users.findAllByPhoneOrderByIdAsc(PHONE))
        .thenReturn(List.of(user(1L, Role.DELIVERY_PARTNER, AccountStatus.SUSPENDED)));

    ApiException e = assertThrows(ApiException.class, () -> service.send(PHONE));
    assertEquals(HttpStatus.FORBIDDEN, e.status());
  }

  @Test
  void missingProviderApiKeyReturnsServiceUnavailableWithCorrelationId() {
    registeredPartner();
    doThrow(new OtpProviderException(OtpProviderException.Reason.CONFIG_MISSING, null, "key missing"))
        .when(sms).send(anyString(), anyString());

    ApiException e = assertThrows(ApiException.class, () -> service.send(PHONE));

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.status());
    assertTrue(e.getMessage().startsWith("OTP service configuration is missing."));
    assertTrue(e.getMessage().contains("Error ID: "));
    verify(challenges).delete(any(OtpChallenge.class));
  }

  @Test
  void rejectedApiKeyReturnsBadGateway() {
    registeredPartner();
    doThrow(new OtpProviderException(OtpProviderException.Reason.AUTH_FAILED, 401, "Invalid API Key"))
        .when(sms).send(anyString(), anyString());

    ApiException e = assertThrows(ApiException.class, () -> service.send(PHONE));
    assertEquals(HttpStatus.BAD_GATEWAY, e.status());
    assertTrue(e.getMessage().startsWith("OTP provider authentication failed."));
  }

  @Test
  void providerTimeoutReturnsGatewayTimeout() {
    registeredPartner();
    doThrow(new OtpProviderException(OtpProviderException.Reason.TIMEOUT, null, "timeout"))
        .when(sms).send(anyString(), anyString());

    assertEquals(HttpStatus.GATEWAY_TIMEOUT,
        assertThrows(ApiException.class, () -> service.send(PHONE)).status());
  }

  @Test
  void malformedProviderResponseReturnsBadGateway() {
    registeredPartner();
    doThrow(new OtpProviderException(OtpProviderException.Reason.MALFORMED_RESPONSE, 200, "<html>"))
        .when(sms).send(anyString(), anyString());

    assertEquals(HttpStatus.BAD_GATEWAY,
        assertThrows(ApiException.class, () -> service.send(PHONE)).status());
  }

  @Test
  void resendWithinCooldownIsRejectedByTheBackend() {
    registeredPartner();
    OtpChallenge recent = challengeFor("111111", Instant.now().plusSeconds(300), 0, false);
    when(challenges.findTopByPhoneOrderByCreatedAtDesc(PHONE)).thenReturn(Optional.of(recent));

    ApiException e = assertThrows(ApiException.class, () -> service.send(PHONE));

    assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.status());
    verify(sms, never()).send(anyString(), anyString());
  }

  @Test
  void repeatedSendsWithinAnHourAreCapped() {
    registeredPartner();
    when(challenges.countByPhoneAndCreatedAtAfter(eq(PHONE), any())).thenReturn(5L);

    ApiException e = assertThrows(ApiException.class, () -> service.send(PHONE));

    assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.status());
    verify(sms, never()).send(anyString(), anyString());
  }

  // ---------- verify ----------

  @Test
  void successfulVerificationIssuesTheExistingPartnerJwtSession() {
    registeredPartner();
    when(challenges.findTopByPhoneOrderByCreatedAtDesc(PHONE))
        .thenReturn(Optional.of(challengeFor("654321", Instant.now().plusSeconds(120), 0, false)));

    LoginResponse response = service.verify("+919959095202", "654321");

    assertNotNull(response);
    assertEquals("access", response.accessToken());
    assertEquals("refresh", response.refreshToken());
    assertEquals("DELIVERY_PARTNER", response.user().role());
    verify(auth).touch(any(UserAccount.class));
  }

  @Test
  void wrongOtpIncrementsAttemptsAndFailsWithBadRequest() {
    OtpChallenge challenge = challengeFor("654321", Instant.now().plusSeconds(120), 0, false);
    when(challenges.findTopByPhoneOrderByCreatedAtDesc(PHONE)).thenReturn(Optional.of(challenge));

    ApiException e = assertThrows(ApiException.class, () -> service.verify(PHONE, "000000"));

    assertEquals(HttpStatus.BAD_REQUEST, e.status());
    assertEquals(1, challenge.getAttempts());
  }

  @Test
  void expiredOtpIsRejected() {
    when(challenges.findTopByPhoneOrderByCreatedAtDesc(PHONE))
        .thenReturn(Optional.of(challengeFor("654321", Instant.now().minusSeconds(1), 0, false)));

    ApiException e = assertThrows(ApiException.class, () -> service.verify(PHONE, "654321"));
    assertEquals(HttpStatus.BAD_REQUEST, e.status());
    assertTrue(e.getMessage().contains("expired"));
  }

  @Test
  void tooManyVerificationAttemptsAreBlocked() {
    when(challenges.findTopByPhoneOrderByCreatedAtDesc(PHONE))
        .thenReturn(Optional.of(challengeFor("654321", Instant.now().plusSeconds(120), 5, false)));

    assertEquals(HttpStatus.TOO_MANY_REQUESTS,
        assertThrows(ApiException.class, () -> service.verify(PHONE, "654321")).status());
  }

  @Test
  void alreadyConsumedOtpCannotBeReused() {
    when(challenges.findTopByPhoneOrderByCreatedAtDesc(PHONE))
        .thenReturn(Optional.of(challengeFor("654321", Instant.now().plusSeconds(120), 1, true)));

    assertEquals(HttpStatus.BAD_REQUEST,
        assertThrows(ApiException.class, () -> service.verify(PHONE, "654321")).status());
  }

  @Test
  void verifyRejectsMalformedOtpAndPhone() {
    assertEquals(HttpStatus.BAD_REQUEST,
        assertThrows(ApiException.class, () -> service.verify(PHONE, "12")).status());
    assertEquals(HttpStatus.BAD_REQUEST,
        assertThrows(ApiException.class, () -> service.verify("12345", "123456")).status());
  }

  @Test
  void verifyWithoutAnyChallengeReturnsBadRequest() {
    when(challenges.findTopByPhoneOrderByCreatedAtDesc(PHONE)).thenReturn(Optional.empty());

    assertEquals(HttpStatus.BAD_REQUEST,
        assertThrows(ApiException.class, () -> service.verify(PHONE, "654321")).status());
  }
}
