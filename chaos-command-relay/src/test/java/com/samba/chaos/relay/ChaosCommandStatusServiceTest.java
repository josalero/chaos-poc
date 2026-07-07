package com.samba.chaos.relay;

import static org.assertj.core.api.Assertions.assertThat;

import com.samba.chaos.listener.message.ChaosCommandAction;
import com.samba.chaos.relay.model.CommandAggregateStatus;
import com.samba.chaos.listener.message.InstanceOutcome;
import com.samba.chaos.relay.InMemoryChaosCommandStore.CommandRecord;
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
}
