package com.nexamart.backend.service;

import com.nexamart.backend.domain.AccountStatus;
import com.nexamart.backend.domain.Role;
import com.nexamart.backend.domain.UserAccount;
import com.nexamart.backend.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Resolves a single user account for a phone number.
 *
 * <p>{@code users.phone} has never had a unique constraint, so historical data can contain more
 * than one row for the same number. {@code Optional<UserAccount> findByPhone(..)} throws
 * {@code IncorrectResultSizeDataAccessException} in that case, which previously surfaced as an
 * opaque HTTP 500 on every login / send-OTP attempt for the affected number. Resolution here is
 * deterministic instead of failing.
 */
@Service
public class UserLookupService {
  private static final Logger log = LoggerFactory.getLogger(UserLookupService.class);

  private final UserAccountRepository users;

  public UserLookupService(UserAccountRepository users) {
    this.users = users;
  }

  /** Deterministically resolves the account for a phone number, tolerating duplicate rows. */
  public Optional<UserAccount> findByPhone(String normalizedPhone) {
    return findByPhonePreferringRole(normalizedPhone, null);
  }

  /** Same as {@link #findByPhone}, but prefers an account with the requested role. */
  public Optional<UserAccount> findByPhonePreferringRole(String normalizedPhone, Role preferred) {
    if (normalizedPhone == null || normalizedPhone.isBlank()) {
      return Optional.empty();
    }
    List<UserAccount> matches = users.findAllByPhoneOrderByIdAsc(normalizedPhone);
    if (matches.isEmpty()) {
      return Optional.empty();
    }
    if (matches.size() > 1) {
      log.warn("Duplicate user rows for the same mobile number. phone={} count={} ids={}",
          com.nexamart.backend.util.PhoneNumbers.mask(normalizedPhone),
          matches.size(),
          matches.stream().map(UserAccount::getId).toList());
    }
    return matches.stream().min(ranking(preferred));
  }

  private Comparator<UserAccount> ranking(Role preferred) {
    return Comparator
        .comparingInt((UserAccount u) -> preferred != null && u.getRole() == preferred ? 0 : 1)
        .thenComparingInt(u -> u.getStatus() == AccountStatus.ACTIVE ? 0 : 1)
        .thenComparingInt(UserLookupService::rolePriority)
        .thenComparing(UserAccount::getId, Comparator.nullsLast(Comparator.naturalOrder()));
  }

  private static int rolePriority(UserAccount user) {
    if (user.getRole() == null) {
      return 9;
    }
    return switch (user.getRole()) {
      case DELIVERY_PARTNER -> 0;
      case ADMIN -> 1;
      default -> 2;
    };
  }
}
