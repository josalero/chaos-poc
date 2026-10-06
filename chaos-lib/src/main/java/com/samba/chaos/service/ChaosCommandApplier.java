package com.samba.chaos.service;

import com.samba.chaos.command.ChaosCommandAction;
import com.samba.chaos.command.ChaosCommandMessage;
import com.samba.chaos.command.ChaosCommandResult;
import com.samba.chaos.command.InstanceOutcome;
import com.samba.chaos.config.ChaosProperties;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Applies a command on this instance and remembers the result for a repeated command id. */
public class ChaosCommandApplier {

  private static final Logger log = LoggerFactory.getLogger(ChaosCommandApplier.class);

  private final String applicationName;
  private final String environment;
  private final String podName;
  private final ChaosActuatorClient actuatorClient;
  private final ChaosExpiryGuard expiryGuard;
  private final ChaosMetrics metrics;
  private final Map<String, ChaosCommandResult> processedResults = new ConcurrentHashMap<>();

  /** Creates an applier bound to one application name. */
  public ChaosCommandApplier(
      String applicationName,
      ChaosProperties properties,
      ChaosActuatorClient actuatorClient,
      ChaosExpiryGuard expiryGuard,
      ChaosMetrics metrics) {
    this.applicationName = applicationName;
    this.environment = properties.getEnvironment();
    this.podName = properties.getPodName();
    this.actuatorClient = actuatorClient;
    this.expiryGuard = expiryGuard;
    this.metrics = metrics;
  }

  /** Validates, applies, and caches the result. A repeated command id returns the cached result. */
  public ChaosCommandResult apply(ChaosCommandMessage message) {
    Optional<String> rejection = rejectionReason(message);
    if (rejection.isPresent()) {
      metrics.commandRejected(rejection.get());
      return result(message, InstanceOutcome.REJECTED, rejection.get(), null);
    }

    String commandId = message.commandId().toString();
    ChaosCommandResult cached = processedResults.get(commandId);
    if (cached != null) {
      if (log.isDebugEnabled()) {
        log.debug("Returning cached result for duplicate commandId={}", message.commandId());
      }
      metrics.duplicateResultRepublished();
      return cached;
    }

    ChaosCommandResult applied = applyOnce(message);
    ChaosCommandResult previous = processedResults.putIfAbsent(commandId, applied);
    return previous != null ? previous : applied;
  }

  private ChaosCommandResult applyOnce(ChaosCommandMessage message) {
    ChaosActuatorClient.ApplyResult result =
        actuatorClient.apply(message.action(), message.assault());
    InstanceOutcome outcome =
        result.succeeded() ? InstanceOutcome.SUCCESS : InstanceOutcome.ACTUATOR_ERROR;
    final ChaosCommandResult commandResult =
        result(message, outcome, result.failedStep(), result.httpStatus());
    if (result.succeeded()) {
      expiryGuard.afterSuccessfulApply(message);
    }
    metrics.commandApplied(outcome);
    if (log.isInfoEnabled()) {
      log.info(
          "Chaos command {} applied on pod {} outcome={}", message.commandId(), podName, outcome);
    }
    return commandResult;
  }

  private Optional<String> rejectionReason(ChaosCommandMessage message) {
    if (!environment.equals(message.environment())) {
      return Optional.of("environment mismatch");
    }
    if (!applicationName.equals(message.targetApplication())) {
      return Optional.of("application mismatch");
    }
    if ((message.action() == ChaosCommandAction.ENABLE
            || message.action() == ChaosCommandAction.CONFIGURE_AND_ENABLE)
        && message.expiresAt() == null) {
      if (log.isWarnEnabled()) {
        log.warn("Ignoring enable command without expiresAt commandId={}", message.commandId());
      }
      return Optional.of("enable command requires expiresAt");
    }
    if (message.expiresAt() != null && message.expiresAt().isBefore(Instant.now())) {
      return Optional.of("command expired");
    }
    return Optional.empty();
  }

  private ChaosCommandResult result(
      ChaosCommandMessage message, InstanceOutcome outcome, String failedStep, Integer httpStatus) {
    return new ChaosCommandResult(
        message.commandId(),
        message.targetApplication(),
        podName,
        outcome,
        failedStep,
        httpStatus,
        Instant.now());
  }
}
