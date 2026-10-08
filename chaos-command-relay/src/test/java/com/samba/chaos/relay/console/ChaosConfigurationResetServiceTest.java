package com.samba.chaos.relay.console;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.samba.chaos.command.ChaosCommandAction;
import com.samba.chaos.relay.config.ChaosRelayProperties;
import com.samba.chaos.relay.model.ChaosCommandSubmitResponse;
import com.samba.chaos.relay.model.ChaosMaintenanceRequest;
import com.samba.chaos.relay.model.CommandAggregateStatus;
import com.samba.chaos.relay.model.ValidationErrorResponse.FieldError;
import com.samba.chaos.relay.service.ChaosCommandStatusService;
import com.samba.chaos.relay.service.ChaosServiceMaintenanceService;
import com.samba.chaos.relay.store.ChaosCommandStore;
import com.samba.chaos.relay.store.CommandRecord;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChaosConfigurationResetServiceTest {

  @Mock private ChaosServiceMaintenanceService maintenanceService;
  @Mock private ChaosCommandStore commandStore;
  @Mock private ChaosCommandStatusService commandStatusService;
  @Mock private ChaosDemoAdminClient demoAdminClient;

  private ChaosConfigurationResetService resetService;

  @BeforeEach
  void setUp() {
    ChaosRelayProperties properties = new ChaosRelayProperties();
    properties.setStatusTimeoutSeconds(5);
    resetService =
        new ChaosConfigurationResetService(
            maintenanceService, commandStore, commandStatusService, demoAdminClient, properties);
  }

  @Test
  void resetConfiguration_disablesAndWaitsForAppliedWithoutClearingDemoData() {
    UUID commandId = UUID.randomUUID();
    CommandRecord record =
        new CommandRecord(
            commandId,
            ChaosCommandAction.DISABLE,
            "chaos-poc-demo",
            1,
            Instant.now(),
            "console-reset",
            "chaos-console",
            null,
            null,
            List.of());

    when(maintenanceService.disable(any(), any()))
        .thenReturn(
            ChaosServiceMaintenanceService.MaintenanceResult.accepted(
                new ChaosCommandSubmitResponse(
                    commandId,
                    CommandAggregateStatus.PUBLISHED,
                    Instant.now(),
                    "chaos-poc-demo",
                    1,
                    "/internal/v1/chaos/commands/" + commandId,
                    "console-reset")));
    when(commandStore.findById(commandId)).thenReturn(Optional.of(record));
    when(commandStatusService.aggregateStatus(record)).thenReturn(CommandAggregateStatus.APPLIED);

    ChaosConfigurationResetService.ResetConfigurationResult result =
        resetService.resetConfiguration(
            "chaos-poc-demo", new ChaosMaintenanceRequest("chaos-console", null, null));

    assertThat(result)
        .isInstanceOf(ChaosConfigurationResetService.ResetConfigurationResult.Success.class);
    verify(demoAdminClient, never()).resetDemoData(any());
  }

  @Test
  void resetConfiguration_returnsCommandNotAppliedWhenDisableFails() {
    UUID commandId = UUID.randomUUID();
    CommandRecord record =
        new CommandRecord(
            commandId,
            ChaosCommandAction.DISABLE,
            "chaos-poc-demo",
            1,
            Instant.now(),
            null,
            "chaos-console",
            null,
            null,
            List.of());

    when(maintenanceService.disable(any(), any()))
        .thenReturn(
            ChaosServiceMaintenanceService.MaintenanceResult.accepted(
                new ChaosCommandSubmitResponse(
                    commandId,
                    CommandAggregateStatus.PUBLISHED,
                    Instant.now(),
                    "chaos-poc-demo",
                    1,
                    "/internal/v1/chaos/commands/" + commandId,
                    null)));
    when(commandStore.findById(commandId)).thenReturn(Optional.of(record));
    when(commandStatusService.aggregateStatus(record)).thenReturn(CommandAggregateStatus.FAILED);

    ChaosConfigurationResetService.ResetConfigurationResult result =
        resetService.resetConfiguration(
            "chaos-poc-demo", new ChaosMaintenanceRequest("chaos-console", null, null));

    assertThat(result)
        .isInstanceOf(
            ChaosConfigurationResetService.ResetConfigurationResult.CommandNotApplied.class);
    verify(demoAdminClient, never()).resetDemoData(any());
  }

  @Test
  void clearDemoData_delegatesToAdminClient() {
    ChaosRelayProperties properties = new ChaosRelayProperties();
    properties.setAllowedTargetApplications(List.of("chaos-poc-demo"));
    resetService =
        new ChaosConfigurationResetService(
            maintenanceService, commandStore, commandStatusService, demoAdminClient, properties);
    when(demoAdminClient.resetDemoData("chaos-poc-demo")).thenReturn(true);

    ChaosConfigurationResetService.ClearDemoDataResult result =
        resetService.clearDemoData("chaos-poc-demo");

    assertThat(result)
        .isInstanceOf(ChaosConfigurationResetService.ClearDemoDataResult.Success.class);
    verify(demoAdminClient).resetDemoData("chaos-poc-demo");
  }

  @Test
  void resetSelected_publishesOneDisablePerNameWithoutWaiting() {
    ChaosRelayProperties properties = new ChaosRelayProperties();
    properties.setAllowedTargetApplications(List.of("chaos-poc-demo", "chaos-poc-downstream"));
    resetService =
        new ChaosConfigurationResetService(
            maintenanceService, commandStore, commandStatusService, demoAdminClient, properties);
    UUID demoId = UUID.randomUUID();
    when(maintenanceService.disable(eq("chaos-poc-demo"), any()))
        .thenReturn(
            ChaosServiceMaintenanceService.MaintenanceResult.accepted(
                new ChaosCommandSubmitResponse(
                    demoId,
                    CommandAggregateStatus.PUBLISHED,
                    Instant.now(),
                    "chaos-poc-demo",
                    1,
                    "/internal/v1/chaos/commands/" + demoId,
                    null)));
    when(maintenanceService.disable(eq("chaos-poc-downstream"), any()))
        .thenReturn(
            ChaosServiceMaintenanceService.MaintenanceResult.rejected(
                List.of(new FieldError("targetApplication", "no UP instances"))));

    ChaosConfigurationResetService.ResetSelectionResult result =
        resetService.resetSelected(
            "chaos-console", List.of("chaos-poc-demo", "chaos-poc-demo", "chaos-poc-downstream"));

    assertThat(result)
        .isInstanceOf(ChaosConfigurationResetService.ResetSelectionResult.Accepted.class);
    ChaosConfigurationResetService.ResetSelectionResult.Accepted accepted =
        (ChaosConfigurationResetService.ResetSelectionResult.Accepted) result;
    assertThat(accepted.services()).hasSize(2);
    assertThat(accepted.services().getFirst().commandId()).isEqualTo(demoId);
    assertThat(accepted.services().get(1).errors()).isNotEmpty();
    verify(commandStatusService, never()).aggregateStatus(any());
    verify(demoAdminClient, never()).resetDemoData(any());
  }

  @Test
  void resetSelected_rejectsEmptyAndUnknownNamesWithoutPublishing() {
    assertThat(resetService.resetSelected("op", List.of()))
        .isInstanceOf(ChaosConfigurationResetService.ResetSelectionResult.Rejected.class);
    assertThat(resetService.resetSelected("op", List.of("unknown")))
        .isInstanceOf(ChaosConfigurationResetService.ResetSelectionResult.Rejected.class);
    verify(maintenanceService, never()).disable(any(), any());
  }

  @Test
  void clearDemoData_reportsNotConfiguredAndFailure() {
    assertThat(resetService.clearDemoData("orders"))
        .isInstanceOf(ChaosConfigurationResetService.ClearDemoDataResult.NotConfigured.class);

    ChaosRelayProperties properties = new ChaosRelayProperties();
    properties.setAllowedTargetApplications(List.of("orders"));
    resetService =
        new ChaosConfigurationResetService(
            maintenanceService, commandStore, commandStatusService, demoAdminClient, properties);
    when(demoAdminClient.resetDemoData("orders")).thenReturn(false);
    assertThat(resetService.clearDemoData("orders"))
        .isInstanceOf(ChaosConfigurationResetService.ClearDemoDataResult.NotConfigured.class);

    when(demoAdminClient.resetDemoData("orders")).thenThrow(new IllegalStateException("down"));
    assertThat(resetService.clearDemoData("orders"))
        .isInstanceOf(ChaosConfigurationResetService.ClearDemoDataResult.Failed.class);
  }

  @Test
  void resetConfiguration_returnsRejectedWhenDisableIsRejected() {
    when(maintenanceService.disable(eq("orders"), any()))
        .thenReturn(
            ChaosServiceMaintenanceService.MaintenanceResult.rejected(
                List.of(new FieldError("targetApplication", "no"))));

    assertThat(
            resetService.resetConfiguration(
                "orders", new ChaosMaintenanceRequest("op", "corr", null)))
        .isInstanceOf(ChaosConfigurationResetService.ResetConfigurationResult.Rejected.class);
  }

  @Test
  void resetConfiguration_waitsUntilTheCommandBecomesTerminal() {
    UUID commandId = UUID.randomUUID();
    CommandRecord record =
        new CommandRecord(
            commandId,
            ChaosCommandAction.DISABLE,
            "orders",
            1,
            Instant.now(),
            null,
            "op",
            null,
            null,
            List.of());
    when(maintenanceService.disable(eq("orders"), any()))
        .thenReturn(
            ChaosServiceMaintenanceService.MaintenanceResult.accepted(
                new ChaosCommandSubmitResponse(
                    commandId,
                    CommandAggregateStatus.PUBLISHED,
                    Instant.now(),
                    "orders",
                    1,
                    "/status",
                    null)));
    when(commandStore.findById(commandId)).thenReturn(Optional.of(record));
    when(commandStatusService.aggregateStatus(record))
        .thenReturn(CommandAggregateStatus.PENDING)
        .thenReturn(CommandAggregateStatus.APPLIED);

    assertThat(
            resetService.resetConfiguration(
                "orders", new ChaosMaintenanceRequest("op", "corr", null)))
        .isInstanceOf(ChaosConfigurationResetService.ResetConfigurationResult.Success.class);
  }

  @Test
  void resetConfiguration_timesOutAndFillsBlankIssuer() {
    ChaosRelayProperties properties = new ChaosRelayProperties();
    properties.setStatusTimeoutSeconds(0);
    resetService =
        new ChaosConfigurationResetService(
            maintenanceService, commandStore, commandStatusService, demoAdminClient, properties);
    UUID commandId = UUID.randomUUID();
    when(maintenanceService.disable(eq("orders"), any()))
        .thenReturn(
            ChaosServiceMaintenanceService.MaintenanceResult.accepted(
                new ChaosCommandSubmitResponse(
                    commandId,
                    CommandAggregateStatus.PUBLISHED,
                    Instant.now(),
                    "orders",
                    1,
                    "/status",
                    null)));
    when(commandStore.findById(commandId)).thenReturn(Optional.empty());

    ChaosConfigurationResetService.ResetConfigurationResult result =
        resetService.resetConfiguration("orders", new ChaosMaintenanceRequest(" ", " ", null));

    assertThat(result)
        .isInstanceOf(
            ChaosConfigurationResetService.ResetConfigurationResult.CommandNotApplied.class);
  }

  @Test
  void resetConfiguration_interruptsWhileWaiting() {
    UUID commandId = UUID.randomUUID();
    CommandRecord record =
        new CommandRecord(
            commandId,
            ChaosCommandAction.DISABLE,
            "orders",
            1,
            Instant.now(),
            null,
            "op",
            null,
            null,
            List.of());
    when(maintenanceService.disable(eq("orders"), any()))
        .thenReturn(
            ChaosServiceMaintenanceService.MaintenanceResult.accepted(
                new ChaosCommandSubmitResponse(
                    commandId,
                    CommandAggregateStatus.PUBLISHED,
                    Instant.now(),
                    "orders",
                    1,
                    "/status",
                    null)));
    when(commandStore.findById(commandId)).thenReturn(Optional.of(record));
    when(commandStatusService.aggregateStatus(record)).thenReturn(CommandAggregateStatus.PENDING);
    Thread.currentThread().interrupt();

    assertThatThrownBy(
            () ->
                resetService.resetConfiguration(
                    "orders", new ChaosMaintenanceRequest("op", "corr", null)))
        .isInstanceOf(IllegalStateException.class);
    assertThat(Thread.interrupted()).isTrue();
  }
}
