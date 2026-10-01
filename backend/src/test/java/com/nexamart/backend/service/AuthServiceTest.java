package com.nexamart.backend.service;

import com.nexamart.backend.api.ApiModels.RegisterRequest;
import com.nexamart.backend.api.ApiModels.RegistrationResponse;
import com.nexamart.backend.config.AppProperties;
import com.nexamart.backend.domain.AccountStatus;
import com.nexamart.backend.domain.DeliveryPartnerProfile;
import com.nexamart.backend.domain.Role;
import com.nexamart.backend.domain.UserAccount;
import com.nexamart.backend.exception.ApiException;
import com.nexamart.backend.repository.DeliveryPartnerProfileRepository;
import com.nexamart.backend.repository.UserAccountRepository;
import com.nexamart.backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {
  private UserAccountRepository users;
  private DeliveryPartnerProfileRepository profiles;
  private BCryptPasswordEncoder encoder;
  private AuthService service;

  @BeforeEach
  void setUp() {
    users = mock(UserAccountRepository.class);
    profiles = mock(DeliveryPartnerProfileRepository.class);
    encoder = new BCryptPasswordEncoder(4);
    JwtService jwt = mock(JwtService.class);
    when(jwt.accessToken(any(), any(), any())).thenReturn("access-token");
    when(jwt.refreshToken(any(), any(), any())).thenReturn("refresh-token");
    when(users.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(users.saveAndFlush(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(profiles.saveAndFlush(any(DeliveryPartnerProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
    service = new AuthService(users, encoder, jwt, mock(AppProperties.class), profiles, new UserLookupService(users));
  }

  @Test
  void registrationCreatesActiveDeliveryPartnerAndProfileWithBcryptPassword() {
    RegisterRequest request = request("new@example.com", "password123", "password123");

    var response = service.register(request);

    var userCaptor = org.mockito.ArgumentCaptor.forClass(UserAccount.class);
    verify(users).saveAndFlush(userCaptor.capture());
    UserAccount user = userCaptor.getValue();
    assertEquals(Role.DELIVERY_PARTNER, user.getRole());
    assertEquals(AccountStatus.ACTIVE, user.getStatus());
    assertNotEquals(request.password(), user.getPasswordHash());
    assertTrue(encoder.matches(request.password(), user.getPasswordHash()));

    var profileCaptor = org.mockito.ArgumentCaptor.forClass(DeliveryPartnerProfile.class);
    verify(profiles).saveAndFlush(profileCaptor.capture());
    assertEquals(user, profileCaptor.getValue().getUser());
    assertEquals("Delivery partner account created successfully.", response.message());
  }

  @Test
  void mismatchedPasswordsFailBeforePersistence() {
    ApiException error = assertThrows(ApiException.class,
      () -> service.register(request("new@example.com", "password123", "different123")));

    assertEquals(HttpStatus.BAD_REQUEST, error.status());
    verify(users, never()).save(any());
    verify(profiles, never()).save(any());
  }

  @Test
  void existingEmailReturnsConflict() {
    when(users.existsByEmailIgnoreCase("existing@example.com")).thenReturn(true);

    ApiException error = assertThrows(ApiException.class,
      () -> service.register(request("existing@example.com", "password123", "password123")));

    assertEquals(HttpStatus.CONFLICT, error.status());
    verify(users, never()).save(any());
    verify(profiles, never()).save(any());
  }

  private RegisterRequest request(String email, String password, String confirmation) {
    return new RegisterRequest("New Partner", email, "9876543210", password, confirmation);
  }
}
