package com.samba.chaos.relay.store;

import com.samba.chaos.command.ChaosAssaultConfig;
import com.samba.chaos.command.ChaosCommandAction;
import com.samba.chaos.command.InstanceOutcome;
import com.samba.chaos.relay.model.ChaosInstanceStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Stored command plus the results reported by each instance. */
public record CommandRecord(
    UUID commandId,
    ChaosCommandAction action,
    String targetApplication,
    int expectedInstances,
    Instant publishedAt,
    String correlationId,
    String issuedBy,
    Instant expiresAt,
    ChaosAssaultConfig assault,
    List<ChaosInstanceStatus> instances) {

  CommandRecord withInstances(List<ChaosInstanceStatus> newInstances) {
    return new CommandRecord(
        commandId,
        action,
        targetApplication,
        expectedInstances,
        publishedAt,
        correlationId,
        issuedBy,
        expiresAt,
        assault,
        List.copyOf(newInstances));
  }

  /** Instances that reported {@code SUCCESS}. */
  public int successCount() {
    return (int) instances.stream().filter(i -> i.outcome() == InstanceOutcome.SUCCESS).count();
  }

  /** Instances that reported anything other than {@code SUCCESS}. */
  public int failureCount() {
    return (int) instances.stream().filter(i -> i.outcome() != InstanceOutcome.SUCCESS).count();
  }
}
