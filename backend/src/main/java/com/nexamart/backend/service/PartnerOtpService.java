package com.nexamart.backend.service;

import com.nexamart.backend.api.ApiModels.*;
import com.nexamart.backend.config.AppProperties;
import com.nexamart.backend.domain.*;
import com.nexamart.backend.exception.ApiException;
import com.nexamart.backend.repository.OtpChallengeRepository;
import com.nexamart.backend.repository.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Service
public class PartnerOtpService {
  private final UserAccountRepository users;
  private final OtpChallengeRepository challenges;
  private final AppProperties props;
  private final AuthService auth;
  private final HttpClient http = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(8))
      .followRedirects(HttpClient.Redirect.NORMAL)
      .build();
  private final SecureRandom random = new SecureRandom();

  public PartnerOtpService(UserAccountRepository users,
                           OtpChallengeRepository challenges,
                           AppProperties props,
                           AuthService auth) {
    this.users = users;
    this.challenges = challenges;
    this.props = props;
    this.auth = auth;
  }

  @Transactional
  public OtpSendResponse send(String raw) {
    String phone = normalize(raw);
    var u = users.findByPhone(phone).orElseThrow(() ->
        new ApiException(HttpStatus.NOT_FOUND,
            "Mobile number is not registered. Please create a delivery partner account first."));
    if (u.getRole() != Role.DELIVERY_PARTNER) {
      throw new ApiException(HttpStatus.NOT_FOUND,
          "This mobile number is not registered as a delivery partner.");
    }

    var prev = challenges.findTopByPhoneOrderByCreatedAtDesc(phone).orElse(null);
    if (prev != null && prev.getCreatedAt().plusSeconds(30).isAfter(Instant.now())) {
      throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
          "Please wait 30 seconds before requesting another OTP.");
    }

    String otp = String.format("%06d", random.nextInt(1_000_000));
    var ch = new OtpChallenge();
    ch.setPhone(phone);
    ch.setOtpHash(hash(phone, otp));
    ch.setExpiresAt(Instant.now().plusSeconds(props.getOtpTtlSeconds()));
    challenges.save(ch);

    try {
      sendSms(phone, otp);
    } catch (Exception e) {
      challenges.delete(ch);
      // Keep the real provider reason in server logs, but don't expose API keys or the OTP.
      System.err.println("2Factor OTP send failed: " + safeMessage(e));
      if (props.isOtpDevMode()) {
        return new OtpSendResponse(true, "OTP generated in development mode.",
            props.getOtpTtlSeconds(), "DEV", otp);
      }
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
          "OTP could not be sent. Please check the 2Factor API key/template configuration in Railway and try again.");
    }

    return new OtpSendResponse(true, "OTP sent successfully.",
        props.getOtpTtlSeconds(), "SMS", null);
  }

  @Transactional
  public LoginResponse verify(String raw, String otp) {
    String phone = normalize(raw);
    if (otp == null || !otp.matches("\\d{6}")) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Enter the 6-digit OTP.");
    }
    var ch = challenges.findTopByPhoneOrderByCreatedAtDesc(phone).orElseThrow(() ->
        new ApiException(HttpStatus.BAD_REQUEST, "OTP not found. Please request a new OTP."));
    if (ch.isVerified()) throw new ApiException(HttpStatus.BAD_REQUEST, "This OTP has already been used.");
    if (ch.getExpiresAt().isBefore(Instant.now())) throw new ApiException(HttpStatus.BAD_REQUEST, "OTP expired. Please request a new OTP.");
    if (ch.getAttempts() >= 5) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many incorrect attempts. Please request a new OTP.");

    ch.setAttempts(ch.getAttempts() + 1);
    if (!MessageDigest.isEqual(ch.getOtpHash().getBytes(StandardCharsets.UTF_8), hash(phone, otp).getBytes(StandardCharsets.UTF_8))) {
      challenges.save(ch);
      throw new ApiException(HttpStatus.BAD_REQUEST, "Incorrect OTP. Please try again.");
    }

    ch.setVerified(true);
    challenges.save(ch);
    UserAccount u = users.findByPhone(phone).orElseThrow(() ->
        new ApiException(HttpStatus.NOT_FOUND, "Delivery partner account not found."));
    if (u.getRole() != Role.DELIVERY_PARTNER) throw new ApiException(HttpStatus.FORBIDDEN, "Delivery partner access required.");
    if (u.getStatus() != AccountStatus.ACTIVE) throw new ApiException(HttpStatus.FORBIDDEN, "Account is not active.");
    u.setLastActiveAt(Instant.now());
    users.save(u);
    return auth.issueDeliveryPartnerSession(u);
  }

  private void sendSms(String phone, String otp) throws Exception {
    String key = props.getOtpApiKey();
    if (key == null || key.isBlank()) {
      throw new IllegalStateException("TWOFACTOR_API_KEY is empty");
    }

    String template = props.getOtpTemplateName();
    if (template == null || template.isBlank()) {
      throw new IllegalStateException("TWOFACTOR_OTP_TEMPLATE is empty");
    }

    // 2Factor's documented custom OTP route uses the API key in the path:
    // /API/V1/{api_key}/SMS/{phone}/AUTOGEN/{template_name}
    // or /SMS/{phone}/{otp}/{template_name}. We generate the OTP locally so
    // verification remains authoritative in our database.
    String encodedKey = URLEncoder.encode(key, StandardCharsets.UTF_8);
    String encodedPhone = URLEncoder.encode("+91" + phone, StandardCharsets.UTF_8);
    String encodedOtp = URLEncoder.encode(otp, StandardCharsets.UTF_8);
    String encodedTemplate = URLEncoder.encode(template, StandardCharsets.UTF_8);

    String url = "https://2factor.in/API/V1/" + encodedKey + "/SMS/"
        + encodedPhone + "/" + encodedOtp + "/" + encodedTemplate;

    HttpRequest request = HttpRequest.newBuilder(URI.create(url))
        .timeout(Duration.ofSeconds(15))
        .header("Accept", "application/json,text/plain,*/*")
        .GET()
        .build();

    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
    String body = response.body() == null ? "" : response.body();
    String lower = body.toLowerCase(Locale.ROOT);

    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new IllegalStateException("2Factor HTTP " + response.statusCode() + ": " + sanitizeProviderBody(body));
    }

    // Legacy 2Factor responses normally contain Status=Success / status=success.
    // Accept both JSON and plain-text response styles used by the service.
    if (!(lower.contains("success") || lower.contains("sent") || lower.contains("details"))) {
      throw new IllegalStateException("2Factor rejected OTP: " + sanitizeProviderBody(body));
    }
  }

  private String sanitizeProviderBody(String body) {
    if (body == null) return "empty response";
    String clean = body.replaceAll("(?i)(api[_-]?key|apikey)[^,} ]*", "[redacted]");
    return clean.length() > 300 ? clean.substring(0, 300) : clean;
  }

  private String safeMessage(Exception e) {
    String message = e.getMessage();
    if (message == null || message.isBlank()) return e.getClass().getSimpleName();
    return message.replaceAll("(?i)(api[_-]?key|apikey)[^,} ]*", "[redacted]");
  }

  private String normalize(String value) {
    String phone = value == null ? "" : value.trim().replace(" ", "").replace("-", "");
    if (phone.startsWith("+91")) phone = phone.substring(3);
    else if (phone.startsWith("0091")) phone = phone.substring(4);
    if (!phone.matches("[6-9]\\d{9}")) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Please enter a valid 10-digit Indian mobile number.");
    }
    return phone;
  }

  private String hash(String phone, String otp) {
    try {
      var md = MessageDigest.getInstance("SHA-256");
      return java.util.HexFormat.of().formatHex(md.digest((phone + ":" + otp).getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
