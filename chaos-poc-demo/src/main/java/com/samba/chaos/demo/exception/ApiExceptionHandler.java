package com.samba.chaos.demo.exception;

import feign.FeignException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(DuplicateOrderException.class)
  public ResponseEntity<Map<String, String>> duplicate(DuplicateOrderException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("error", "CONFLICT", "message", ex.getMessage()));
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<Map<String, String>> resourceNotFound(ResourceNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(Map.of("error", "NOT_FOUND", "message", ex.getMessage()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> notFound(IllegalArgumentException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(Map.of("error", "NOT_FOUND", "message", ex.getMessage()));
  }

  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<Map<String, String>> forbidden(ForbiddenException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(Map.of("error", "FORBIDDEN", "message", ex.getMessage()));
  }

  @ExceptionHandler(FeignException.class)
  public ResponseEntity<Map<String, String>> feignError(FeignException ex) {
    HttpStatus status =
        HttpStatus.resolve(ex.status()) != null
            ? HttpStatus.valueOf(ex.status())
            : HttpStatus.BAD_GATEWAY;
    return ResponseEntity.status(status)
        .body(Map.of("error", status.name(), "message", "Downstream call failed"));
  }

  @ExceptionHandler(RuntimeException.class)
  public ResponseEntity<Map<String, String>> internalServerError(RuntimeException ex) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(
            Map.of(
                "error", "INTERNAL_SERVER_ERROR",
                "message", "Injected server failure"));
  }
}
