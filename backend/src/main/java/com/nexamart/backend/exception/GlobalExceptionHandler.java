package com.nexamart.backend.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  ResponseEntity<?> api(ApiException e) {
    return ResponseEntity.status(e.status()).body(body(e.getMessage(), null));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<?> validation(MethodArgumentNotValidException e) {
    String m = e.getBindingResult().getFieldErrors().stream().findFirst()
        .map(x -> x.getField() + ": " + x.getDefaultMessage()).orElse("Validation failed");
    return ResponseEntity.badRequest().body(body(m, null));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<?> uploadTooLarge(MaxUploadSizeExceededException e) {
    return ResponseEntity.status(413).body(body("Image is too large. Maximum 5 MB.", null));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<?> dataIntegrity(DataIntegrityViolationException e) {
    log.warn("Data integrity conflict", e);
    return ResponseEntity.status(409)
        .body(body("The account details are already in use or conflict with existing data.", null));
  }

  /**
   * A single-result query matched several rows (for example duplicate accounts sharing the same
   * mobile number). This is a data conflict, not an unexpected server failure.
   */
  @ExceptionHandler(IncorrectResultSizeDataAccessException.class)
  ResponseEntity<?> duplicateRows(IncorrectResultSizeDataAccessException e, HttpServletRequest request) {
    String errorId = UUID.randomUUID().toString();
    log.error("[ERROR_ID={}] Duplicate records matched a single-result query. method={} path={}",
        errorId, request.getMethod(), request.getRequestURI(), e);
    return ResponseEntity.status(409).body(body(
        "This mobile number is linked to more than one account. Please contact support.", errorId));
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  ResponseEntity<?> conflict(ObjectOptimisticLockingFailureException e) {
    return ResponseEntity.status(409)
        .body(body("The order was updated by another user. Refresh and try again.", null));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> generic(Exception e, HttpServletRequest request) {
    String errorId = UUID.randomUUID().toString();
    log.error("[ERROR_ID={}] Unhandled API error. method={} path={}",
        errorId, request.getMethod(), request.getRequestURI(), e);
    return ResponseEntity.internalServerError()
        .body(body("Something went wrong. Please try again.", errorId));
  }

  private static Map<String, Object> body(String message, String errorId) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("message", message == null ? "Request failed." : message);
    if (errorId != null) {
      payload.put("errorId", errorId);
    }
    payload.put("timestamp", Instant.now().toString());
    return payload;
  }
}
