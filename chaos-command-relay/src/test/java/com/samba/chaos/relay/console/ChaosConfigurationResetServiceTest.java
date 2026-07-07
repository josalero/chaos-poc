package com.samba.chaos.relay.console;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.samba.chaos.listener.message.ChaosCommandAction;
import com.samba.chaos.relay.model.ChaosCommandSubmitResponse;
import com.samba.chaos.relay.model.ChaosMaintenanceRequest;
import com.samba.chaos.relay.model.CommandAggregateStatus;
import com.samba.chaos.relay.ChaosCommandStatusService;
import com.samba.chaos.relay.ChaosCommandStore;
import com.samba.chaos.relay.ChaosRelayProperties;
import com.samba.chaos.relay.ChaosServiceMaintenanceService;
import com.samba.chaos.relay.InMemoryChaosCommandStore.CommandRecord;
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
            maintenanceService,
            commandStore,
            commandStatusService,
            demoAdminClient,
            properties);
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
            maintenanceService,
            commandStore,
            commandStatusService,
            demoAdminClient,
            properties);
    when(demoAdminClient.resetDemoData("chaos-poc-demo")).thenReturn(true);

    ChaosConfigurationResetService.ClearDemoDataResult result =
        resetService.clearDemoData("chaos-poc-demo");

    assertThat(result)
        .isInstanceOf(ChaosConfigurationResetService.ClearDemoDataResult.Success.class);
    verify(demoAdminClient).resetDemoData("chaos-poc-demo");
  }

  @Test
  void resetAllConfigurations_resetsEveryAllowedTargetWithoutClearingDemoData() {
    ChaosRelayProperties properties = new ChaosRelayProperties();
    properties.setStatusTimeoutSeconds(5);
    properties.setAllowedTargetApplications(
        List.of("chaos-poc-demo", "chaos-poc-downstream"));
    resetService =
        new ChaosConfigurationResetService(
            maintenanceService,
            commandStore,
            commandStatusService,
            demoAdminClient,
            properties);

    stubSuccessfulReset("chaos-poc-demo");
    stubSuccessfulReset("chaos-poc-downstream");

    ChaosConfigurationResetService.ResetAllConfigurationResult result =
        resetService.resetAllConfigurations(
            new ChaosMaintenanceRequest("chaos-console", "reset-all-test", null));

    assertThat(result.allSucceeded()).isTrue();
    assertThat(result.outcomes()).hasSize(2);
    verify(demoAdminClient, never()).resetDemoData(any());
  }

  private void stubSuccessfulReset(String applicationName) {
    UUID commandId = UUID.randomUUID();
    CommandRecord record =
        new CommandRecord(
            commandId,
            ChaosCommandAction.DISABLE,
            applicationName,
            1,
            Instant.now(),
            "console-reset",
            "chaos-console",
            null,
            null,
            List.of());

    when(maintenanceService.disable(eq(applicationName), any()))
        .thenReturn(
            ChaosServiceMaintenanceService.MaintenanceResult.accepted(
                new ChaosCommandSubmitResponse(
                    commandId,
                    CommandAggregateStatus.PUBLISHED,
                    Instant.now(),
                    applicationName,
                    1,
                    "/internal/v1/chaos/commands/" + commandId,
                    "console-reset")));
    when(commandStore.findById(commandId)).thenReturn(Optional.of(record));
    when(commandStatusService.aggregateStatus(record)).thenReturn(CommandAggregateStatus.APPLIED);
  }
}
