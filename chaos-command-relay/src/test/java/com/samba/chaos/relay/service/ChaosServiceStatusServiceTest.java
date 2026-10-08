package com.samba.chaos.relay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.samba.chaos.command.ChaosAssaultConfig;
import com.samba.chaos.command.ChaosCommandAction;
import com.samba.chaos.relay.config.ChaosRelayProperties;
import com.samba.chaos.relay.console.ChaosMonkeyActuatorProbe;
import com.samba.chaos.relay.console.ChaosMonkeyRuntimeSnapshot;
import com.samba.chaos.relay.console.ChaosMonkeyStatusCache;
import com.samba.chaos.relay.console.ChaosMonkeyStatusCache.Entry;
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
import org.springframework.cloud.client.DefaultServiceInstance;

class ChaosServiceStatusServiceTest {

  private ChaosCommandStore store;
  private ChaosCommandStatusService statusService;
  private TargetInstancesResolver instancesResolver;
  private ChaosMonkeyActuatorProbe probe;
  private ChaosMonkeyStatusCache statusCache;
  private ChaosServiceStatusService service;

  @BeforeEach
  void setUp() {
    store = mock(ChaosCommandStore.class);
    statusService = mock(ChaosCommandStatusService.class);
    instancesResolver = mock(TargetInstancesResolver.class);
    probe = mock(ChaosMonkeyActuatorProbe.class);
    statusCache = mock(ChaosMonkeyStatusCache.class);
    ChaosRelayProperties properties = new ChaosRelayProperties();
    properties.setAllowedTargetApplications(List.of("orders"));
    when(instancesResolver.resolveUp("orders")).thenReturn(List.of());
    when(probe.probe("orders")).thenReturn(ChaosMonkeyRuntimeSnapshot.unconfigured());
    when(statusCache.get("orders")).thenReturn(Optional.empty());
    when(store.findByApplication(
            org.mockito.ArgumentMatchers.eq("orders"), org.mockito.ArgumentMatchers.anyInt()))
        .thenReturn(List.of());
    service =
        new ChaosServiceStatusService(
            store, properties, statusService, instancesResolver, probe, statusCache);
  }

  @Test
  void unknownServiceIsEmpty() {
    assertThat(service.getService("billing")).isEmpty();
    assertThat(service.getHistory("billing", 50)).isEmpty();
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
              assertThat(summary.cmEnabled()).isNull();
              assertThat(summary.cmCheckedAt()).isNull();
            });
    verify(probe, never()).probe(org.mockito.ArgumentMatchers.any());
    assertThat(service.getHistory("orders", 50)).contains(List.of());
    assertThat(service.getService("orders").orElseThrow().upInstanceIds()).isEmpty();
  }

  @Test
  void listsDiscoveryInstanceIds() {
    when(instancesResolver.resolveUp("orders"))
        .thenReturn(
            List.of(
                new DefaultServiceInstance("pod-a", "orders", "10.0.0.1", 8080, false),
                new DefaultServiceInstance("pod-b", "orders", "10.0.0.2", 8080, false)));
    when(store.findLatestByApplication("orders")).thenReturn(Optional.empty());

    assertThat(service.getService("orders").orElseThrow().upInstanceIds())
        .containsExactly("pod-a", "pod-b");
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

  @Test
  void listUsesCacheAndLatestCommand() {
    Instant checkedAt = Instant.parse("2026-10-07T12:00:00Z");
    when(statusCache.get("orders")).thenReturn(Optional.of(new Entry(true, 1, 1, checkedAt)));
    CommandRecord record = record(ChaosCommandAction.CONFIGURE_AND_ENABLE);
    when(store.findLatestByApplication("orders")).thenReturn(Optional.of(record));
    when(statusService.aggregateStatus(record)).thenReturn(CommandAggregateStatus.APPLIED);

    assertThat(service.listServices())
        .singleElement()
        .satisfies(
            summary -> {
              assertThat(summary.cmEnabled()).isTrue();
              assertThat(summary.cmCheckedAt()).isEqualTo(checkedAt);
              assertThat(summary.lastCommandStatus()).isEqualTo(CommandAggregateStatus.APPLIED);
              assertThat(summary.expectedInstances()).isEqualTo(1);
            });
  }

  @Test
  void historyLimitIsClamped() {
    service.getHistory("orders", 0);
    service.getHistory("orders", 500);

    verify(store).findByApplication(eq("orders"), eq(1));
    verify(store).findByApplication(eq("orders"), eq(200));
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
