package com.samba.chaos.relay.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.samba.chaos.command.ChaosCommandAction;
import com.samba.chaos.relay.model.ChaosCommandRequest;
import com.samba.chaos.relay.model.ChaosCommandStatusResponse;
import com.samba.chaos.relay.model.ChaosCommandSubmitResponse;
import com.samba.chaos.relay.model.CommandAggregateStatus;
import com.samba.chaos.relay.model.ValidationErrorResponse;
import com.samba.chaos.relay.model.ValidationErrorResponse.FieldError;
import com.samba.chaos.relay.service.ChaosCommandService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ChaosCommandControllerTest {

  private final ChaosCommandService commandService = mock(ChaosCommandService.class);
  private final ChaosCommandController controller = new ChaosCommandController(commandService);

  @Test
  void mapsAcceptedRejectedUnavailableAndMissingStatus() {
    ChaosCommandRequest request =
        new ChaosCommandRequest(
            null, "test", "orders", ChaosCommandAction.DISABLE, null, null, "op", null);
    UUID commandId = UUID.randomUUID();
    when(commandService.submit(request))
        .thenReturn(
            ChaosCommandService.SubmitResult.accepted(
                new ChaosCommandSubmitResponse(
                    commandId,
                    CommandAggregateStatus.PUBLISHED,
                    Instant.now(),
                    "orders",
                    1,
                    "/status",
                    null)))
        .thenReturn(
            ChaosCommandService.SubmitResult.rejected(
                new ValidationErrorResponse(
                    "REJECTED", List.of(new FieldError("environment", "no")))))
        .thenReturn(
            ChaosCommandService.SubmitResult.unavailable(
                new ValidationErrorResponse(
                    "NO_INSTANCES", List.of(new FieldError("targetApplication", "none")))));
    when(commandService.getStatus(commandId))
        .thenReturn(Optional.of(mock(ChaosCommandStatusResponse.class)))
        .thenReturn(Optional.empty());

    assertThat(controller.submit(request).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(controller.submit(request).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(controller.submit(request).getStatusCode())
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(controller.getStatus(commandId).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(controller.getStatus(commandId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
