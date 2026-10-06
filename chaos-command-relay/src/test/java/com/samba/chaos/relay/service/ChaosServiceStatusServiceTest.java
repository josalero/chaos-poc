package com.samba.chaos.relay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.samba.chaos.command.ChaosAssaultConfig;
import com.samba.chaos.command.ChaosCommandAction;
import com.samba.chaos.relay.config.ChaosRelayProperties;
import com.samba.chaos.relay.console.ChaosMonkeyActuatorProbe;
import com.samba.chaos.relay.console.ChaosMonkeyRuntimeSnapshot;
import com.samba.chaos.relay.model.CommandAggregateStatus;
import com.samba.chaos.relay.model.ServiceConfigState;
import com.samba.chaos.relay.store.ChaosCommandStore;
import com.samba.chaos.relay.store.CommandRecord;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChaosServiceStatusServiceTest {

  private ChaosCommandStore store;
  private ChaosCommandStatusService statusService;
  private TargetInstancesResolver instancesResolver;
  private ChaosMonkeyActuatorProbe probe;
  private ChaosServiceStatusService service;

  @BeforeEach
  void setUp() {
    store = mock(ChaosCommandStore.class);
    statusService = mock(ChaosCommandStatusService.class);
    instancesResolver = mock(TargetInstancesResolver.class);
    probe = mock(ChaosMonkeyActuatorProbe.class);
    ChaosRelayProperties properties = new ChaosRelayProperties();
    properties.setAllowedTargetApplications(List.of("orders"));
    when(instancesResolver.resolveUp("orders")).thenReturn(List.of());
    when(probe.probe("orders")).thenReturn(ChaosMonkeyRuntimeSnapshot.unconfigured());
    service =
        new ChaosServiceStatusService(store, properties, statusService, instancesResolver, probe);
  }

  @Test
  void unknownServiceIsEmpty() {
    assertThat(service.getService("billing")).isEmpty();
    assertThat(service.getHistory("billing")).isEmpty();
    assertThat(service.probe("billing")).isEmpty();
  }

  @Test
  void returnsTheActuatorSnapshotForAnAllowlistedService() {
    ChaosMonkeyRuntimeSnapshot snapshot = ChaosMonkeyRuntimeSnapshot.ok(true, "{}", "{}");
    when(probe.probe("orders")).thenReturn(snapshot);

    assertThat(service.probe("orders")).contains(snapshot);
  }

  @Test
  void listsDefaultStateWhenNoCommandExists() {
    when(store.findLatestByApplication("orders")).thenReturn(Optional.empty());

    assertThat(service.listServices())
        .singleElement()
        .satisfies(
            summary -> {
              assertThat(summary.configState()).isEqualTo(ServiceConfigState.DEFAULT);
              assertThat(summary.cmEnabled()).isFalse();
            });
    assertThat(service.getHistory("orders")).contains(List.of());
  }

  @Test
  void usesLiveProbeAndAppliedAssault() {
    CommandRecord record = record(ChaosCommandAction.CONFIGURE_AND_ENABLE);
    when(store.findLatestByApplication("orders")).thenReturn(Optional.of(record));
    when(statusService.aggregateStatus(record)).thenReturn(CommandAggregateStatus.APPLIED);
    when(probe.probe("orders")).thenReturn(ChaosMonkeyRuntimeSnapshot.ok(true, "{}", "{}"));

    assertThat(service.getService("orders"))
        .get()
        .satisfies(
            response -> {
              assertThat(response.configState()).isEqualTo(ServiceConfigState.APPLIED);
              assertThat(response.cmEnabled()).isTrue();
              assertThat(response.appliedAssault()).isNotNull();
            });
  }

  @Test
  void fallsBackToMemoryWhenProbeIsUnreachable() {
    CommandRecord record = record(ChaosCommandAction.DISABLE);
    when(store.findLatestByApplication("orders")).thenReturn(Optional.of(record));
    when(statusService.aggregateStatus(record)).thenReturn(CommandAggregateStatus.APPLIED);

    assertThat(service.getService("orders"))
        .get()
        .satisfies(
            response -> {
              assertThat(response.configState()).isEqualTo(ServiceConfigState.DEFAULT);
              assertThat(response.cmEnabled()).isFalse();
            });

    CommandRecord enabled = record(ChaosCommandAction.ENABLE);
    when(store.findLatestByApplication("orders")).thenReturn(Optional.of(enabled));
    when(statusService.aggregateStatus(enabled)).thenReturn(CommandAggregateStatus.APPLIED);
    assertThat(service.getService("orders").orElseThrow().cmEnabled()).isTrue();
  }

  @Test
  void mapsNonAppliedCommandStatuses() {
    assertConfigState(CommandAggregateStatus.FAILED, ServiceConfigState.FAILED);
    assertConfigState(CommandAggregateStatus.PENDING, ServiceConfigState.DESIRED);
    assertConfigState(CommandAggregateStatus.PARTIAL, ServiceConfigState.DESIRED);
    assertConfigState(CommandAggregateStatus.TIMED_OUT, ServiceConfigState.PARTIAL);
    assertConfigState(CommandAggregateStatus.PUBLISHED, ServiceConfigState.DEFAULT);
  }

  private void assertConfigState(CommandAggregateStatus status, ServiceConfigState expected) {
    CommandRecord record = record(ChaosCommandAction.ENABLE);
    when(store.findLatestByApplication("orders")).thenReturn(Optional.of(record));
    when(statusService.aggregateStatus(record)).thenReturn(status);

    assertThat(service.getService("orders").orElseThrow().configState()).isEqualTo(expected);
  }

  private static CommandRecord record(ChaosCommandAction action) {
    return new CommandRecord(
        UUID.randomUUID(),
        action,
        "orders",
        1,
        Instant.now(),
        "corr",
        "op",
        Instant.now().plusSeconds(60),
        new ChaosAssaultConfig(1, true, false, false, null, null, null, null),
        List.of());
  }
}
