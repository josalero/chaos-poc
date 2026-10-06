package com.samba.chaos.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.samba.chaos.command.InstanceOutcome;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class ChaosMetricsTest {

  @Test
  void recordsAppliedRejectedRepeatedAndAutomaticDisableCounters() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    ChaosMetrics metrics = new ChaosMetrics(registry);

    metrics.commandApplied(InstanceOutcome.SUCCESS);
    metrics.duplicateResultRepublished();
    metrics.commandRejected("environment mismatch");
    metrics.automaticDisable(false);

    assertThat(registry.counter("chaos.commands.applied", "outcome", "success").count())
        .isEqualTo(1);
    assertThat(registry.counter("chaos.command.results.republished").count()).isEqualTo(1);
    assertThat(
            registry.counter("chaos.commands.rejected", "reason", "environment mismatch").count())
        .isEqualTo(1);
    assertThat(registry.counter("chaos.expiry.auto_disable", "outcome", "failure").count())
        .isEqualTo(1);
  }
}
