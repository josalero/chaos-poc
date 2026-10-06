package com.samba.chaos.demo.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/** Member. */
@RestControllerAdvice
public class ApiExceptionHandler {

  /** Member. */
  @ExceptionHandler(DuplicateOrderException.class)
  public ResponseEntity<Map<String, String>> duplicate(DuplicateOrderException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("error", "CONFLICT", "message", ex.getMessage()));
  }

  /** Member. */
  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<Map<String, String>> resourceNotFound(ResourceNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(Map.of("error", "NOT_FOUND", "message", ex.getMessage()));
  }

  /** Member. */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> notFound(IllegalArgumentException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(Map.of("error", "NOT_FOUND", "message", ex.getMessage()));
  }

  /** Member. */
  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<Map<String, String>> forbidden(ForbiddenException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(Map.of("error", "FORBIDDEN", "message", ex.getMessage()));
  }

  /** Member. */
  @ExceptionHandler(RestClientResponseException.class)
  public ResponseEntity<Map<String, String>> downstreamHttpError(RestClientResponseException ex) {
    HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
    if (status == null) {
      status = HttpStatus.BAD_GATEWAY;
    }
    return ResponseEntity.status(status)
        .body(Map.of("error", status.name(), "message", "Downstream call failed"));
  }

  /** Member. */
  @ExceptionHandler(ResourceAccessException.class)
  public ResponseEntity<Map<String, String>> downstreamUnreachable(ResourceAccessException ex) {
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
        .body(Map.of("error", "BAD_GATEWAY", "message", "Downstream call failed"));
  }

  /** Member. */
  @ExceptionHandler(RuntimeException.class)
  public ResponseEntity<Map<String, String>> internalServerError(RuntimeException ex) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(
            Map.of(
                "error", "INTERNAL_SERVER_ERROR",
                "message", "Injected server failure"));
  }
}
