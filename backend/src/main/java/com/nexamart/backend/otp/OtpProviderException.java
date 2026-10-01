package com.nexamart.backend.otp;

import org.springframework.http.HttpStatus;

/**
 * Thrown when the SMS OTP provider could not deliver an OTP.
 *
 * <p>The {@link Reason} drives both the HTTP status and the safe, user-facing message, so the
 * Android app never sees a generic "Something went wrong" for a known provider problem and the
 * backend logs always keep the real provider detail.
 */
public class OtpProviderException extends RuntimeException {

  public enum Reason {
    CONFIG_MISSING(HttpStatus.SERVICE_UNAVAILABLE, "OTP service configuration is missing. Please contact support."),
    AUTH_FAILED(HttpStatus.BAD_GATEWAY, "OTP provider authentication failed. Please contact support."),
    INVALID_PHONE(HttpStatus.BAD_REQUEST, "OTP provider rejected the mobile number."),
    INVALID_TEMPLATE(HttpStatus.SERVICE_UNAVAILABLE, "OTP SMS configuration is invalid. Please contact support."),
    INSUFFICIENT_BALANCE(HttpStatus.SERVICE_UNAVAILABLE, "OTP service is temporarily unavailable. Please try again shortly."),
    TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "OTP provider timed out. Please try again."),
    MALFORMED_RESPONSE(HttpStatus.BAD_GATEWAY, "OTP provider returned an unexpected response. Please try again."),
    UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "OTP provider is temporarily unavailable. Please try again.");

    private final HttpStatus status;
    private final String userMessage;

    Reason(HttpStatus status, String userMessage) {
      this.status = status;
      this.userMessage = userMessage;
    }

    public HttpStatus status() {
      return status;
    }

    public String userMessage() {
      return userMessage;
    }
  }

  private final Reason reason;
  private final Integer providerHttpStatus;
  private final String providerDetail;

  public OtpProviderException(Reason reason, Integer providerHttpStatus, String providerDetail, Throwable cause) {
    super(reason.name() + " providerStatus=" + providerHttpStatus + " detail=" + providerDetail, cause);
    this.reason = reason;
    this.providerHttpStatus = providerHttpStatus;
    this.providerDetail = providerDetail;
  }

  public OtpProviderException(Reason reason, Integer providerHttpStatus, String providerDetail) {
    this(reason, providerHttpStatus, providerDetail, null);
  }

  public Reason reason() {
    return reason;
  }

  public Integer providerHttpStatus() {
    return providerHttpStatus;
  }

  public String providerDetail() {
    return providerDetail;
  }
}
