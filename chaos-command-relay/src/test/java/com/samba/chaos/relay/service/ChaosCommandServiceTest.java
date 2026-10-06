package com.samba.chaos.relay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.samba.chaos.command.ChaosCommandAction;
import com.samba.chaos.relay.model.ChaosCommandRequest;
import com.samba.chaos.relay.model.ValidationErrorResponse;
import com.samba.chaos.relay.model.ValidationErrorResponse.FieldError;
import com.samba.chaos.relay.store.ChaosCommandStore;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChaosCommandServiceTest {

  @Test
  void rejectsInvalidRequestBeforeDiscovery() {
    ChaosCommandValidator validator = mock(ChaosCommandValidator.class);
    ChaosCommandRequest request =
        new ChaosCommandRequest(
            null, "production", "orders", ChaosCommandAction.DISABLE, null, null, "op", null);
    when(validator.validate(request))
        .thenReturn(List.of(new FieldError("environment", "must be 'test'")));
    when(validator.toErrorResponse(org.mockito.ArgumentMatchers.anyList()))
        .thenReturn(
            new ValidationErrorResponse(
                "REJECTED", List.of(new FieldError("environment", "must be 'test'"))));
    ChaosCommandService service =
        new ChaosCommandService(validator, mock(TargetInstancesResolver.class), null, null, null);

    ChaosCommandService.SubmitResult result = service.submit(request);

    assertThat(result).isInstanceOf(ChaosCommandService.SubmitResult.Rejected.class);
  }

  @Test
  void returnsEmptyStatusWhenCommandIsUnknown() {
    ChaosCommandStore store = mock(ChaosCommandStore.class);
    UUID commandId = UUID.randomUUID();
    when(store.findById(commandId)).thenReturn(Optional.empty());
    ChaosCommandService service =
        new ChaosCommandService(null, null, null, store, mock(ChaosCommandStatusService.class));

    assertThat(service.getStatus(commandId)).isEmpty();
  }
}
