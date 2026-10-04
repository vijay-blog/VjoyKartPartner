package com.nexamart.backend.otp;

import com.nexamart.backend.config.AppProperties;
import com.nexamart.backend.util.PhoneNumbers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 2Factor SMS OTP integration.
 *
 * <p>Endpoint (transactional SMS OTP with a backend-generated code):
 * <pre>GET {base-url}/API/V1/{api_key}/SMS/{10-digit-phone}/{otp}[/{template_name}]</pre>
 *
 * <p>2Factor answers with {@code {"Status":"Success","Details":"<session-id>"}} or
 * {@code {"Status":"Error","Details":"<reason>"}} — frequently with HTTP 200 even for errors, so
 * the body must always be parsed. Nothing secret (API key / OTP) is ever logged.
 */
@Component
public class TwoFactorOtpSmsSender implements OtpSmsSender {
  private static final Logger log = LoggerFactory.getLogger(TwoFactorOtpSmsSender.class);

  private static final Pattern STATUS = Pattern.compile("\"Status\"\\s*:\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
  private static final Pattern DETAILS = Pattern.compile("\"Details\"\\s*:\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);

  private final AppProperties props;
  private final HttpClient http;

  @Autowired
  public TwoFactorOtpSmsSender(AppProperties props) {
    this(props, HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build());
  }

  TwoFactorOtpSmsSender(AppProperties props, HttpClient http) {
    this.props = props;
    this.http = http;
  }

  @Override
  public boolean isConfigured() {
    String key = props.getOtpApiKey();
    return key != null && !key.isBlank();
  }

  @Override
  public void send(String nationalPhone, String otp) {
    if (!isConfigured()) {
      throw new OtpProviderException(OtpProviderException.Reason.CONFIG_MISSING, null,
          "TWOFACTOR_API_KEY is not set in the environment (accepted names: TWOFACTOR_API_KEY, TWOFORCE_API_KEY, TWO_FACTOR_API_KEY)");
    }

    HttpResponse<String> response;
    try {
      response = http.send(buildRequest(nationalPhone, otp), HttpResponse.BodyHandlers.ofString());
    } catch (HttpTimeoutException e) {
      throw new OtpProviderException(OtpProviderException.Reason.TIMEOUT, null, "provider timed out", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new OtpProviderException(OtpProviderException.Reason.UNAVAILABLE, null, "request interrupted", e);
    } catch (Exception e) {
      throw new OtpProviderException(OtpProviderException.Reason.UNAVAILABLE, null,
          e.getClass().getSimpleName() + ": " + redact(e.getMessage()), e);
    }

    int status = response.statusCode();
    String body = response.body() == null ? "" : response.body().trim();
    String providerStatus = group(STATUS, body);
    String providerDetail = group(DETAILS, body);
    String safeDetail = redact(providerDetail != null ? providerDetail : body);

    log.info("OTP provider response: status={} providerStatus={} phone={}",
        status, providerStatus, PhoneNumbers.mask(nationalPhone));

    if (status == 401 || status == 403) {
      throw new OtpProviderException(OtpProviderException.Reason.AUTH_FAILED, status, safeDetail);
    }
    if (status >= 500) {
      throw new OtpProviderException(OtpProviderException.Reason.UNAVAILABLE, status, safeDetail);
    }
    if (body.isEmpty()) {
      throw new OtpProviderException(OtpProviderException.Reason.MALFORMED_RESPONSE, status, "empty response body");
    }
    if (providerStatus == null) {
      throw new OtpProviderException(OtpProviderException.Reason.MALFORMED_RESPONSE, status, safeDetail);
    }
    if ("success".equalsIgnoreCase(providerStatus)) {
      return;
    }
    throw new OtpProviderException(classify(safeDetail), status, safeDetail);
  }

  private HttpRequest buildRequest(String nationalPhone, String otp) {
    String base = props.getOtpBaseUrl() == null || props.getOtpBaseUrl().isBlank()
        ? "https://2factor.in"
        : props.getOtpBaseUrl().trim();
    while (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    if (!base.toLowerCase(Locale.ROOT).contains("/api/v1")) {
      base = base + "/API/V1";
    }

    StringBuilder url = new StringBuilder(base)
        .append('/').append(enc(props.getOtpApiKey().trim()))
        .append("/SMS")
        .append('/').append(enc(nationalPhone))
        .append('/').append(enc(otp));

    String template = props.getOtpTemplateName();
    if (template != null && !template.isBlank()) {
      url.append('/').append(enc(template.trim()));
    }

    return HttpRequest.newBuilder(URI.create(url.toString()))
        .timeout(Duration.ofSeconds(Math.max(1, props.getOtpTimeoutSeconds())))
        .header("Accept", "application/json,text/plain,*/*")
        .header("User-Agent", "VJoyKart-Partner-Backend/1.0")
        .GET()
        .build();
  }

  private static OtpProviderException.Reason classify(String detail) {
    String d = detail == null ? "" : detail.toLowerCase(Locale.ROOT);
    if (d.contains("api key") || d.contains("apikey") || d.contains("invalid key")
        || d.contains("account") && d.contains("suspend")) {
      return OtpProviderException.Reason.AUTH_FAILED;
    }
    if (d.contains("balance") || d.contains("credit")) {
      return OtpProviderException.Reason.INSUFFICIENT_BALANCE;
    }
    if (d.contains("template") || d.contains("sender") || d.contains("dlt") || d.contains("header")) {
      return OtpProviderException.Reason.INVALID_TEMPLATE;
    }
    if (d.contains("phone") || d.contains("mobile") || d.contains("number") || d.contains("recipient")) {
      return OtpProviderException.Reason.INVALID_PHONE;
    }
    return OtpProviderException.Reason.UNAVAILABLE;
  }

  private String enc(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static String group(Pattern pattern, String body) {
    Matcher m = pattern.matcher(body);
    return m.find() ? m.group(1) : null;
  }

  /** Strips anything that could echo back the API key and caps the length for logging. */
  private static String redact(String value) {
    if (value == null || value.isBlank()) {
      return "empty";
    }
    String clean = value.replaceAll("(?i)(api[\\s_-]?key)\\s*[:=]?\\s*\\S+", "$1=[redacted]");
    clean = clean.replaceAll("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", "[redacted]");
    return clean.length() > 300 ? clean.substring(0, 300) : clean;
  }
}
