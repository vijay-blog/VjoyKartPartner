package com.nexamart.backend.repository;
import com.nexamart.backend.domain.OtpChallenge; import org.springframework.data.jpa.repository.JpaRepository; import java.time.Instant; import java.util.Optional;
public interface OtpChallengeRepository extends JpaRepository<OtpChallenge,Long>{
  Optional<OtpChallenge> findTopByPhoneOrderByCreatedAtDesc(String phone);
  long countByPhoneAndCreatedAtAfter(String phone, Instant since);
}
