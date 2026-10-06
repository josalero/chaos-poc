package com.samba.chaos.relay.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.samba.chaos.command.ChaosCommandAction;
import com.samba.chaos.command.InstanceOutcome;
import com.samba.chaos.relay.config.ChaosRelayProperties;
import com.samba.chaos.relay.model.CommandAggregateStatus;
import com.samba.chaos.relay.store.CommandRecord;
import com.samba.chaos.relay.store.InMemoryChaosCommandStore;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChaosCommandStatusServiceTest {

  private ChaosCommandStatusService statusService;
  private ChaosRelayProperties properties;

  @BeforeEach
  void setUp() {
    properties = new ChaosRelayProperties();
    properties.setStatusTimeoutSeconds(30);
    statusService = new ChaosCommandStatusService(new InMemoryChaosCommandStore(), properties);
  }

  @Test
  void shouldReturnAppliedWhenAllInstancesSucceed() {
    CommandRecord record =
        new CommandRecord(
            UUID.randomUUID(),
            ChaosCommandAction.CONFIGURE_AND_ENABLE,
            "chaos-poc-demo",
            2,
            Instant.now(),
            "corr",
            "operator",
            Instant.now().plusSeconds(3600),
            null,
            List.of(
                new com.samba.chaos.relay.model.ChaosInstanceStatus(
                    "pod-a", InstanceOutcome.SUCCESS, Instant.now(), null, 200),
                new com.samba.chaos.relay.model.ChaosInstanceStatus(
                    "pod-b", InstanceOutcome.SUCCESS, Instant.now(), null, 200)));

    assertThat(statusService.aggregateStatus(record)).isEqualTo(CommandAggregateStatus.APPLIED);
  }

  @Test
  void shouldReturnFailedWhenAnyInstanceFails() {
    CommandRecord record =
        new CommandRecord(
            UUID.randomUUID(),
            ChaosCommandAction.ENABLE,
            "chaos-poc-demo",
            1,
            Instant.now(),
            null,
            "operator",
            Instant.now().plusSeconds(3600),
            null,
            List.of(
                new com.samba.chaos.relay.model.ChaosInstanceStatus(
                    "pod-a", InstanceOutcome.ACTUATOR_ERROR, Instant.now(), "enable", 503)));

    assertThat(statusService.aggregateStatus(record)).isEqualTo(CommandAggregateStatus.FAILED);
  }

  @Test
  void shouldReturnPendingWhenNothingHasReported() {
    CommandRecord record = record(1, List.of(), Instant.now());

    assertThat(statusService.aggregateStatus(record)).isEqualTo(CommandAggregateStatus.PENDING);
    assertThat(statusService.toStatusResponse(record).completedAt()).isNull();
    assertThat(statusService.toStatusResponse(record).issuedBy()).isEqualTo("operator");
    assertThat(statusService.toStatusResponse(record).assault()).isNull();
  }

  @Test
  void shouldReturnPartialWhenSomeInstancesSucceedBeforeTimeout() {
    CommandRecord record =
        record(
            2,
            List.of(
                new com.samba.chaos.relay.model.ChaosInstanceStatus(
                    "pod-a", InstanceOutcome.SUCCESS, Instant.now(), null, 200)),
            Instant.now());

    assertThat(statusService.aggregateStatus(record)).isEqualTo(CommandAggregateStatus.PARTIAL);
  }

  @Test
  void shouldReturnTimedOutWhenTheDeadlineHasPassed() {
    CommandRecord record = record(2, List.of(), Instant.now().minusSeconds(120));

    assertThat(statusService.aggregateStatus(record)).isEqualTo(CommandAggregateStatus.TIMED_OUT);
    assertThat(statusService.toStatusResponse(record).completedAt()).isNotNull();
    assertThat(statusService.toSubmitResponse(record).status())
        .isEqualTo(CommandAggregateStatus.PUBLISHED);
  }

  private static CommandRecord record(
      int expected,
      List<com.samba.chaos.relay.model.ChaosInstanceStatus> instances,
      Instant publishedAt) {
    return new CommandRecord(
        UUID.randomUUID(),
        ChaosCommandAction.ENABLE,
        "chaos-poc-demo",
        expected,
        publishedAt,
        "corr",
        "operator",
        Instant.now().plusSeconds(3600),
        null,
        instances);
  }
}
