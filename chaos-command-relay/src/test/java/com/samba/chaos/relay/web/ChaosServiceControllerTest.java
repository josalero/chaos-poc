package com.samba.chaos.relay.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.samba.chaos.command.ChaosCommandAction;
import com.samba.chaos.relay.console.ChaosConfigurationResetService;
import com.samba.chaos.relay.console.ChaosConfigurationResetService.ClearDemoDataResult;
import com.samba.chaos.relay.console.ChaosConfigurationResetService.ResetAllConfigurationResult;
import com.samba.chaos.relay.console.ChaosConfigurationResetService.ResetConfigurationResult;
import com.samba.chaos.relay.console.ChaosMonkeyRuntimeSnapshot;
import com.samba.chaos.relay.model.ChaosClearDemoDataResponse;
import com.samba.chaos.relay.model.ChaosCommandRequest;
import com.samba.chaos.relay.model.ChaosCommandStatusResponse;
import com.samba.chaos.relay.model.ChaosCommandSubmitResponse;
import com.samba.chaos.relay.model.ChaosConfigurationResetAllResponse;
import com.samba.chaos.relay.model.ChaosMaintenanceRequest;
import com.samba.chaos.relay.model.ChaosServiceStatusResponse;
import com.samba.chaos.relay.model.ChaosServiceStatusSummary;
import com.samba.chaos.relay.model.CommandAggregateStatus;
import com.samba.chaos.relay.model.ValidationErrorResponse;
import com.samba.chaos.relay.model.ValidationErrorResponse.FieldError;
import com.samba.chaos.relay.service.ChaosCommandService;
import com.samba.chaos.relay.service.ChaosCommandValidator;
import com.samba.chaos.relay.service.ChaosServiceMaintenanceService;
import com.samba.chaos.relay.service.ChaosServiceStatusService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ChaosServiceControllerTest {

  private ChaosServiceStatusService statusService;
  private ChaosServiceMaintenanceService maintenanceService;
  private ChaosConfigurationResetService resetService;
  private ChaosCommandService commandService;
  private ChaosCommandValidator validator;
  private ChaosServiceController controller;

  @BeforeEach
  void setUp() {
    statusService = mock(ChaosServiceStatusService.class);
    maintenanceService = mock(ChaosServiceMaintenanceService.class);
    resetService = mock(ChaosConfigurationResetService.class);
    commandService = mock(ChaosCommandService.class);
    validator = mock(ChaosCommandValidator.class);
    when(validator.toErrorResponse(org.mockito.ArgumentMatchers.anyList()))
        .thenReturn(new ValidationErrorResponse("REJECTED", List.of()));
    controller =
        new ChaosServiceController(
            statusService, maintenanceService, resetService, commandService, validator);
  }

  @Test
  void readsServiceStatusAndHistory() {
    when(statusService.listServices()).thenReturn(List.of(mock(ChaosServiceStatusSummary.class)));
    when(statusService.getService("orders"))
        .thenReturn(Optional.of(mock(ChaosServiceStatusResponse.class)))
        .thenReturn(Optional.empty());
    when(statusService.getHistory("orders"))
        .thenReturn(Optional.of(List.of(mock(ChaosCommandStatusResponse.class))))
        .thenReturn(Optional.empty());
    when(statusService.probe("orders"))
        .thenReturn(Optional.of(ChaosMonkeyRuntimeSnapshot.unconfigured()))
        .thenReturn(Optional.empty());

    assertThat(controller.listServices()).hasSize(1);
    assertThat(controller.getService("orders").getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(controller.getService("orders").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(controller.getServiceHistory("orders").getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(controller.getServiceHistory("orders").getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(controller.actuator("orders").getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(controller.actuator("orders").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void mapsMaintenanceEnableAndDisable() {
    ChaosMaintenanceRequest request = new ChaosMaintenanceRequest("op", null, Instant.now());
    when(maintenanceService.enable("orders", request))
        .thenReturn(ChaosServiceMaintenanceService.MaintenanceResult.accepted(submitResponse()));
    when(maintenanceService.disable("orders", request))
        .thenReturn(
            ChaosServiceMaintenanceService.MaintenanceResult.rejected(
                List.of(new FieldError("expiresAt", "required"))));

    assertThat(controller.enable("orders", request).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(controller.disable("orders", request).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void mapsResetOutcomes() {
    ChaosMaintenanceRequest request = new ChaosMaintenanceRequest("op", null, null);
    UUID commandId = UUID.randomUUID();
    when(resetService.resetConfiguration("orders", request))
        .thenReturn(ResetConfigurationResult.success(commandId))
        .thenReturn(ResetConfigurationResult.rejected(List.of(new FieldError("environment", "no"))))
        .thenReturn(
            ResetConfigurationResult.commandNotApplied(commandId, CommandAggregateStatus.FAILED));

    assertThat(controller.reset("orders", request).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(controller.reset("orders", request).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(controller.reset("orders", request).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void mapsClearDemoDataOutcomes() {
    when(resetService.clearDemoData("orders"))
        .thenReturn(new ClearDemoDataResult.Success())
        .thenReturn(new ClearDemoDataResult.NotConfigured())
        .thenReturn(new ClearDemoDataResult.Failed("down"));

    assertThat(controller.clearDemoData("orders").getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(controller.clearDemoData("orders").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    var failed = controller.clearDemoData("orders");
    assertThat(failed.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    assertThat(failed.getBody()).isInstanceOf(ChaosClearDemoDataResponse.class);
  }

  @Test
  void mapsResetAllAndCommandSubmit() {
    ChaosMaintenanceRequest request = new ChaosMaintenanceRequest("op", "reset", null);
    UUID commandId = UUID.randomUUID();
    when(resetService.resetAllConfigurations(request))
        .thenReturn(
            new ResetAllConfigurationResult(
                List.of(
                    new ResetAllConfigurationResult.ServiceResetOutcome(
                        "orders", ResetConfigurationResult.success(commandId)),
                    new ResetAllConfigurationResult.ServiceResetOutcome(
                        "billing",
                        ResetConfigurationResult.rejected(List.of(new FieldError("target", "no")))),
                    new ResetAllConfigurationResult.ServiceResetOutcome(
                        "gateway",
                        ResetConfigurationResult.commandNotApplied(
                            commandId, CommandAggregateStatus.TIMED_OUT)))));
    ChaosCommandRequest matching =
        new ChaosCommandRequest(
            null, "test", "orders", ChaosCommandAction.DISABLE, null, null, "op", null);
    ChaosCommandRequest mismatched =
        new ChaosCommandRequest(
            null, "test", "billing", ChaosCommandAction.DISABLE, null, null, "op", null);
    when(commandService.submit(matching))
        .thenReturn(ChaosCommandService.SubmitResult.accepted(submitResponse()))
        .thenReturn(
            ChaosCommandService.SubmitResult.rejected(
                new ValidationErrorResponse("REJECTED", List.of())))
        .thenReturn(
            ChaosCommandService.SubmitResult.unavailable(
                new ValidationErrorResponse("NO_INSTANCES", List.of())));

    ChaosConfigurationResetAllResponse body =
        (ChaosConfigurationResetAllResponse) controller.resetAll(request).getBody();
    assertThat(body.services()).hasSize(3);
    assertThat(controller.submitForService("orders", mismatched).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(controller.submitForService("orders", matching).getStatusCode())
        .isEqualTo(HttpStatus.ACCEPTED);
    assertThat(controller.submitForService("orders", matching).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(controller.submitForService("orders", matching).getStatusCode())
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }

  private static ChaosCommandSubmitResponse submitResponse() {
    return new ChaosCommandSubmitResponse(
        UUID.randomUUID(),
        CommandAggregateStatus.PUBLISHED,
        Instant.now(),
        "orders",
        1,
        "/status",
        "corr");
  }
}
