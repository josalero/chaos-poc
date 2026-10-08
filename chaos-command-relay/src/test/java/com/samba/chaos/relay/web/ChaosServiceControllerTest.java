package com.samba.chaos.relay.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.samba.chaos.command.ChaosCommandAction;
import com.samba.chaos.relay.console.ChaosConfigurationResetService;
import com.samba.chaos.relay.console.ChaosConfigurationResetService.ClearDemoDataResult;
import com.samba.chaos.relay.console.ChaosConfigurationResetService.ResetConfigurationResult;
import com.samba.chaos.relay.console.ChaosConfigurationResetService.ResetSelectionResult;
import com.samba.chaos.relay.console.ChaosMonkeyRuntimeSnapshot;
import com.samba.chaos.relay.model.ChaosCatalogEntry;
import com.samba.chaos.relay.model.ChaosCatalogSaveRequest;
import com.samba.chaos.relay.model.ChaosClearDemoDataResponse;
import com.samba.chaos.relay.model.ChaosCommandRequest;
import com.samba.chaos.relay.model.ChaosCommandStatusResponse;
import com.samba.chaos.relay.model.ChaosCommandSubmitResponse;
import com.samba.chaos.relay.model.ChaosMaintenanceRequest;
import com.samba.chaos.relay.model.ChaosResetSelectionRequest;
import com.samba.chaos.relay.model.ChaosResetSelectionResponse;
import com.samba.chaos.relay.model.ChaosServiceStatusResponse;
import com.samba.chaos.relay.model.ChaosServiceStatusSummary;
import com.samba.chaos.relay.model.CommandAggregateStatus;
import com.samba.chaos.relay.model.ValidationErrorResponse;
import com.samba.chaos.relay.model.ValidationErrorResponse.FieldError;
import com.samba.chaos.relay.service.ChaosCatalogService;
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
  private ChaosCatalogService catalogService;
  private ChaosCommandValidator validator;
  private ChaosServiceController controller;

  @BeforeEach
  void setUp() {
    statusService = mock(ChaosServiceStatusService.class);
    maintenanceService = mock(ChaosServiceMaintenanceService.class);
    resetService = mock(ChaosConfigurationResetService.class);
    commandService = mock(ChaosCommandService.class);
    catalogService = mock(ChaosCatalogService.class);
    validator = mock(ChaosCommandValidator.class);
    when(validator.toErrorResponse(org.mockito.ArgumentMatchers.anyList()))
        .thenReturn(new ValidationErrorResponse("REJECTED", List.of()));
    controller =
        new ChaosServiceController(
            statusService,
            maintenanceService,
            resetService,
            commandService,
            catalogService,
            validator);
  }

  @Test
  void readsServiceStatusAndHistory() {
    when(statusService.listServices()).thenReturn(List.of(mock(ChaosServiceStatusSummary.class)));
    when(statusService.getService("orders"))
        .thenReturn(Optional.of(mock(ChaosServiceStatusResponse.class)))
        .thenReturn(Optional.empty());
    when(statusService.getHistory("orders", 50))
        .thenReturn(Optional.of(List.of(mock(ChaosCommandStatusResponse.class))))
        .thenReturn(Optional.empty());
    when(statusService.probe("orders"))
        .thenReturn(Optional.of(ChaosMonkeyRuntimeSnapshot.unconfigured()))
        .thenReturn(Optional.empty());

    assertThat(controller.listServices()).hasSize(1);
    assertThat(controller.getService("orders").getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(controller.getService("orders").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(controller.getServiceHistory("orders", 50).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(controller.getServiceHistory("orders", 50).getStatusCode())
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
  void mapsResetSelectedAndCommandSubmit() {
    UUID commandId = UUID.randomUUID();
    ChaosResetSelectionRequest request =
        new ChaosResetSelectionRequest("op", List.of("orders", "billing"));
    when(resetService.resetSelected("op", List.of("orders", "billing")))
        .thenReturn(
            new ResetSelectionResult.Accepted(
                List.of(
                    new ResetSelectionResult.ServiceReset(
                        "orders", commandId, "/internal/v1/chaos/commands/" + commandId, List.of()),
                    new ResetSelectionResult.ServiceReset(
                        "billing", null, null, List.of(new FieldError("target", "no"))))));
    when(resetService.resetSelected("op", List.of()))
        .thenReturn(
            new ResetSelectionResult.Rejected(
                List.of(new FieldError("applicationNames", "at least one service is required"))));
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

    var accepted = controller.resetSelected(request);
    assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(((ChaosResetSelectionResponse) accepted.getBody()).services()).hasSize(2);
    assertThat(
            controller
                .resetSelected(new ChaosResetSelectionRequest("op", List.of()))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(controller.submitForService("orders", mismatched).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(controller.submitForService("orders", matching).getStatusCode())
        .isEqualTo(HttpStatus.ACCEPTED);
    assertThat(controller.submitForService("orders", matching).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(controller.submitForService("orders", matching).getStatusCode())
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }

  @Test
  void mapsCatalogListSaveAndDelete() {
    UUID catalogId = UUID.randomUUID();
    ChaosCatalogEntry entry =
        new ChaosCatalogEntry(
            catalogId, "orders", "Latency", ChaosCommandAction.DISABLE, null, Instant.now());
    ChaosCatalogSaveRequest request =
        new ChaosCatalogSaveRequest("Latency", ChaosCommandAction.DISABLE, null);
    when(catalogService.list("orders"))
        .thenReturn(Optional.of(List.of(entry)))
        .thenReturn(Optional.empty());
    when(catalogService.save("orders", request))
        .thenReturn(Optional.of(entry))
        .thenReturn(Optional.empty());
    when(catalogService.delete("orders", catalogId))
        .thenReturn(Optional.of(true))
        .thenReturn(Optional.of(false))
        .thenReturn(Optional.empty());

    assertThat(controller.listCatalog("orders").getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(controller.listCatalog("orders").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(controller.saveCatalog("orders", request).getBody()).isEqualTo(entry);
    assertThat(controller.saveCatalog("orders", request).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(controller.deleteCatalog("orders", catalogId).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(controller.deleteCatalog("orders", catalogId).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(controller.deleteCatalog("orders", catalogId).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
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
