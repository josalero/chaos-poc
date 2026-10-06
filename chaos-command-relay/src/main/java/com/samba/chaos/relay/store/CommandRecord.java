package com.samba.chaos.relay.store;

import com.samba.chaos.command.ChaosAssaultConfig;
import com.samba.chaos.command.ChaosCommandAction;
import com.samba.chaos.command.InstanceOutcome;
import com.samba.chaos.relay.model.ChaosInstanceStatus;
import com.samba.chaos.relay.model.InstanceSelection;
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
    List<ChaosInstanceStatus> instances,
    InstanceSelection instanceSelection,
    List<String> instanceIds) {

  /**
   * Record written before instance selection was stored. Scope is ALL.
   *
   * @param commandId stored id
   * @param action command action
   * @param targetApplication Eureka application name
   * @param expectedInstances instances the command was sent to
   * @param publishedAt when the relay stored the command
   * @param correlationId caller reference
   * @param issuedBy operator or client name
   * @param expiresAt lease end
   * @param assault assault that was sent
   * @param instances results received so far
   */
  public CommandRecord(
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
    this(
        commandId,
        action,
        targetApplication,
        expectedInstances,
        publishedAt,
        correlationId,
        issuedBy,
        expiresAt,
        assault,
        instances,
        InstanceSelection.ALL,
        List.of());
  }

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
        List.copyOf(newInstances),
        instanceSelection,
        instanceIds);
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
