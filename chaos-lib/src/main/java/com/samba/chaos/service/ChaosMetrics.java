package com.samba.chaos.service;

import com.samba.chaos.command.InstanceOutcome;
import io.micrometer.core.instrument.MeterRegistry;

/** Counters for applied, rejected, repeated, and automatically disabled commands. */
public class ChaosMetrics {

  private final MeterRegistry meterRegistry;

  /** Records counters on the given registry. */
  public ChaosMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  /** Increments {@code chaos.commands.applied} for the outcome. */
  public void commandApplied(InstanceOutcome outcome) {
    meterRegistry
        .counter("chaos.commands.applied", "outcome", outcome.name().toLowerCase())
        .increment();
  }

  /** Increments {@code chaos.command.results.republished}. */
  public void duplicateResultRepublished() {
    meterRegistry.counter("chaos.command.results.republished").increment();
  }

  /** Increments {@code chaos.commands.rejected} tagged with the rejection reason. */
  public void commandRejected(String reason) {
    meterRegistry.counter("chaos.commands.rejected", "reason", reason).increment();
  }

  /** Increments {@code chaos.expiry.auto_disable}. */
  public void automaticDisable(boolean succeeded) {
    meterRegistry
        .counter("chaos.expiry.auto_disable", "outcome", succeeded ? "success" : "failure")
        .increment();
  }
}
