package com.samba.chaos.relay.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.samba.chaos.command.ChaosCommandAction;
import com.samba.chaos.command.InstanceOutcome;
import com.samba.chaos.relay.model.ChaosInstanceStatus;
import com.samba.chaos.relay.model.InstanceSelection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CommandRecordTest {

  @Test
  void countsOutcomesAndCopiesInstances() {
    Instant publishedAt = Instant.parse("2026-10-07T00:00:00Z");
    CommandRecord record =
        new CommandRecord(
            UUID.randomUUID(),
            ChaosCommandAction.DISABLE,
            "orders",
            2,
            publishedAt,
            "corr",
            "tester",
            null,
            null,
            List.of(
                new ChaosInstanceStatus("pod-a", InstanceOutcome.SUCCESS, publishedAt, null, 200),
                new ChaosInstanceStatus(
                    "pod-b", InstanceOutcome.ACTUATOR_ERROR, publishedAt, "enable", 500)));

    assertThat(record.successCount()).isEqualTo(1);
    assertThat(record.failureCount()).isEqualTo(1);
    assertThat(record.withInstances(List.of()).instances()).isEmpty();
    assertThat(record.instanceSelection()).isEqualTo(InstanceSelection.ALL);
    assertThat(record.instanceIds()).isEmpty();
    assertThat(record.correlationId()).isEqualTo("corr");
    assertThat(record.issuedBy()).isEqualTo("tester");
    assertThat(record.expiresAt()).isNull();
    assertThat(record.assault()).isNull();
    assertThat(record.expectedInstances()).isEqualTo(2);
    assertThat(record.targetApplication()).isEqualTo("orders");
    assertThat(record.action()).isEqualTo(ChaosCommandAction.DISABLE);
    assertThat(record.publishedAt()).isEqualTo(publishedAt);
  }
}
