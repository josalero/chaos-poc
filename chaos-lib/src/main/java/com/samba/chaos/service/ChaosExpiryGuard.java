package com.samba.chaos.service;

import com.samba.chaos.command.ChaosCommandAction;
import com.samba.chaos.command.ChaosCommandMessage;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Enforces enable-command expiry locally so an abandoned experiment cannot remain active. */
public class ChaosExpiryGuard {

  private static final Logger log = LoggerFactory.getLogger(ChaosExpiryGuard.class);

  private final ChaosActuatorClient actuatorClient;
  private final ScheduledExecutorService scheduler;
  private final ChaosMetrics metrics;
  private final Object monitor = new Object();
  private UUID activeCommandId;
  private ScheduledFuture<?> pendingDisable;

  /** Creates a guard that disables Chaos Monkey on a daemon thread when a lease expires. */
  public ChaosExpiryGuard(ChaosActuatorClient actuatorClient, ChaosMetrics metrics) {
    this(
        actuatorClient,
        metrics,
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "chaos-expiry-guard");
              thread.setDaemon(true);
              return thread;
            }));
  }

  ChaosExpiryGuard(
      ChaosActuatorClient actuatorClient,
      ChaosMetrics metrics,
      ScheduledExecutorService scheduler) {
    this.actuatorClient = actuatorClient;
    this.metrics = metrics;
    this.scheduler = scheduler;
  }

  /**
   * Schedules a disable at {@code expiresAt} after a successful enable. A disable cancels it.
   *
   * @param message command that was just applied
   */
  public void afterSuccessfulApply(ChaosCommandMessage message) {
    synchronized (monitor) {
      if (message.action() == ChaosCommandAction.DISABLE) {
        cancelPendingDisable();
        activeCommandId = null;
        return;
      }
      if (message.action() != ChaosCommandAction.ENABLE
          && message.action() != ChaosCommandAction.CONFIGURE_AND_ENABLE) {
        return;
      }

      cancelPendingDisable();
      activeCommandId = message.commandId();
      long delayMillis =
          Math.max(0, Duration.between(Instant.now(), message.expiresAt()).toMillis());
      pendingDisable =
          scheduler.schedule(
              () -> disableIfStillActive(message.commandId()), delayMillis, TimeUnit.MILLISECONDS);
      if (log.isInfoEnabled()) {
        log.info(
            "Scheduled automatic Chaos Monkey disable commandId={} expiresAt={}",
            message.commandId(),
            message.expiresAt());
      }
    }
  }

  private void disableIfStillActive(UUID commandId) {
    synchronized (monitor) {
      if (!commandId.equals(activeCommandId)) {
        return;
      }
      ChaosActuatorClient.ApplyResult result =
          actuatorClient.apply(ChaosCommandAction.DISABLE, null);
      if (result.succeeded()) {
        activeCommandId = null;
        pendingDisable = null;
        if (log.isInfoEnabled()) {
          log.info("Automatically disabled expired Chaos Monkey commandId={}", commandId);
        }
      } else if (log.isErrorEnabled()) {
        log.error(
            "Automatic Chaos Monkey disable failed commandId={} step={} status={}",
            commandId,
            result.failedStep(),
            result.httpStatus());
      }
      metrics.automaticDisable(result.succeeded());
    }
  }

  private void cancelPendingDisable() {
    if (pendingDisable != null) {
      pendingDisable.cancel(false);
      pendingDisable = null;
    }
  }

  @PreDestroy
  void shutdown() {
    scheduler.shutdownNow();
  }
}
