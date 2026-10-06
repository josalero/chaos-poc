package com.samba.chaos.demo.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

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

  @Test
  void shouldMapDownstreamHttpStatusOntoTheDemoResponse() {
    RestClientResponseException downstream =
        new RestClientResponseException("forbidden", 403, "Forbidden", null, new byte[0], null);

    ResponseEntity<Map<String, String>> response = handler.downstreamHttpError(downstream);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody()).containsEntry("error", "FORBIDDEN");
    assertThat(response.getBody()).containsEntry("message", "Downstream call failed");
  }

  @Test
  void shouldMapDownstreamServerErrorOntoTheDemoResponse() {
    RestClientResponseException downstream =
        new RestClientResponseException(
            "failed", HttpStatusCode.valueOf(500), "Error", null, new byte[0], null);

    ResponseEntity<Map<String, String>> response = handler.downstreamHttpError(downstream);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody()).containsEntry("message", "Downstream call failed");
  }

  @Test
  void mapsDomainExceptionsAndUnknownDownstreamStatus() {
    assertThat(handler.duplicate(new DuplicateOrderException("ABC")).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(handler.resourceNotFound(new ResourceNotFoundException("missing")).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(handler.notFound(new IllegalArgumentException("missing")).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(handler.forbidden(new ForbiddenException("denied")).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(handler.downstreamUnreachable(new ResourceAccessException("down")).getStatusCode())
        .isEqualTo(HttpStatus.BAD_GATEWAY);

    RestClientResponseException unknown =
        new RestClientResponseException("weird", 599, "weird", null, new byte[0], null);
    assertThat(handler.downstreamHttpError(unknown).getStatusCode())
        .isEqualTo(HttpStatus.BAD_GATEWAY);
  }
}
