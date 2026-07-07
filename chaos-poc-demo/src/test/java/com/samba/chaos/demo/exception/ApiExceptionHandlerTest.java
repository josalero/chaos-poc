package com.samba.chaos.demo.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ApiExceptionHandlerTest {

  private final ApiExceptionHandler handler = new ApiExceptionHandler();

  @Test
  void shouldMapUnexpectedRuntimeExceptionToSafeHttp500Response() {
    ResponseEntity<Map<String, String>> response =
        handler.internalServerError(new RuntimeException("sensitive failure detail"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "error", "INTERNAL_SERVER_ERROR",
                "message", "Injected server failure"));
  }
}
