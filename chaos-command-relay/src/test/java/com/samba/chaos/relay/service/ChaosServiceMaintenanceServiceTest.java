package com.samba.chaos.relay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.samba.chaos.command.ChaosAssaultConfig;
import com.samba.chaos.command.ChaosCommandAction;
import com.samba.chaos.command.InstanceOutcome;
import com.samba.chaos.relay.model.ChaosCommandSubmitResponse;
import com.samba.chaos.relay.model.ChaosInstanceStatus;
import com.samba.chaos.relay.model.ChaosMaintenanceRequest;
import com.samba.chaos.relay.model.CommandAggregateStatus;
import com.samba.chaos.relay.model.ValidationErrorResponse;
import com.samba.chaos.relay.model.ValidationErrorResponse.FieldError;
import com.samba.chaos.relay.store.ChaosCommandStore;
import com.samba.chaos.relay.store.CommandRecord;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChaosServiceMaintenanceServiceTest {

  @Test
  void enableReusesLatestAppliedAssault() {
    ChaosAssaultConfig assault = new ChaosAssaultConfig(1, true, false, true, 10, 20, null, null);
    ChaosCommandStore store = mock(ChaosCommandStore.class);
    ChaosCommandStatusService statusService = mock(ChaosCommandStatusService.class);
    ChaosCommandService commandService = mock(ChaosCommandService.class);
    UUID previous = UUID.randomUUID();
    CommandRecord configured =
        new CommandRecord(
            previous,
            ChaosCommandAction.CONFIGURE,
            "orders",
            1,
            Instant.now().minusSeconds(30),
            null,
            "op",
            null,
            assault,
            List.of(
                new ChaosInstanceStatus(
                    "pod-a", InstanceOutcome.SUCCESS, Instant.now(), null, 200)));
    when(store.findAll()).thenReturn(List.of(configured));
    when(statusService.aggregateStatus(configured)).thenReturn(CommandAggregateStatus.APPLIED);
    UUID commandId = UUID.randomUUID();
    when(commandService.submit(any()))
        .thenReturn(
            ChaosCommandService.SubmitResult.accepted(
                new ChaosCommandSubmitResponse(
                    commandId,
                    CommandAggregateStatus.PUBLISHED,
                    Instant.now(),
                    "orders",
                    1,
                    "/status",
                    "corr")));
    ChaosServiceMaintenanceService service =
        new ChaosServiceMaintenanceService(commandService, store, statusService);

    ChaosServiceMaintenanceService.MaintenanceResult result =
        service.enable(
            "orders", new ChaosMaintenanceRequest("op", "corr", Instant.now().plusSeconds(60)));

    assertThat(result)
        .isInstanceOf(ChaosServiceMaintenanceService.MaintenanceResult.Accepted.class);
  }

  @Test
  void enableRejectsWhenNoPriorAssaultExists() {
    ChaosServiceMaintenanceService service =
        new ChaosServiceMaintenanceService(
            mock(ChaosCommandService.class),
            mock(ChaosCommandStore.class),
            mock(ChaosCommandStatusService.class));

    assertThat(
            service.enable(
                "orders", new ChaosMaintenanceRequest("op", null, Instant.now().plusSeconds(30))))
        .isInstanceOf(ChaosServiceMaintenanceService.MaintenanceResult.Rejected.class);
  }

  @Test
  void enableRejectsWhenExpiryIsMissing() {
    ChaosAssaultConfig assault =
        new ChaosAssaultConfig(1, true, false, false, null, null, null, null);
    ChaosCommandStore store = mock(ChaosCommandStore.class);
    ChaosCommandStatusService statusService = mock(ChaosCommandStatusService.class);
    CommandRecord configured =
        new CommandRecord(
            UUID.randomUUID(),
            ChaosCommandAction.CONFIGURE_AND_ENABLE,
            "orders",
            1,
            Instant.now(),
            null,
            "op",
            null,
            assault,
            List.of());
    when(store.findAll()).thenReturn(List.of(configured));
    when(statusService.aggregateStatus(configured)).thenReturn(CommandAggregateStatus.APPLIED);
    ChaosServiceMaintenanceService service =
        new ChaosServiceMaintenanceService(mock(ChaosCommandService.class), store, statusService);

    assertThat(service.enable("orders", new ChaosMaintenanceRequest("op", null, null)))
        .isInstanceOf(ChaosServiceMaintenanceService.MaintenanceResult.Rejected.class);
  }

  @Test
  void disableAndResetSurfaceRejectedAndUnavailableSubmits() {
    ChaosCommandService commandService = mock(ChaosCommandService.class);
    when(commandService.submit(any()))
        .thenReturn(
            ChaosCommandService.SubmitResult.rejected(
                new ValidationErrorResponse(
                    "REJECTED", List.of(new FieldError("environment", "must be 'test'")))))
        .thenReturn(
            ChaosCommandService.SubmitResult.unavailable(
                new ValidationErrorResponse(
                    "NO_INSTANCES", List.of(new FieldError("targetApplication", "none")))));
    ChaosServiceMaintenanceService service =
        new ChaosServiceMaintenanceService(
            commandService, mock(ChaosCommandStore.class), mock(ChaosCommandStatusService.class));
    ChaosMaintenanceRequest request = new ChaosMaintenanceRequest("op", null, null);

    assertThat(service.disable("orders", request))
        .isInstanceOf(ChaosServiceMaintenanceService.MaintenanceResult.Rejected.class);
    assertThat(service.reset("orders", request))
        .isInstanceOf(ChaosServiceMaintenanceService.MaintenanceResult.Rejected.class);
  }
}
