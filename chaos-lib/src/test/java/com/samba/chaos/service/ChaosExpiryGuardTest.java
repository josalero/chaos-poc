package com.samba.chaos.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.samba.chaos.command.ChaosCommandAction;
import com.samba.chaos.command.ChaosCommandMessage;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ChaosExpiryGuardTest {

  @Test
  void automaticallyDisablesAnExpiredEnableCommand() throws Exception {
    ChaosActuatorClient actuatorClient = mock(ChaosActuatorClient.class);
    CountDownLatch disabled = new CountDownLatch(1);
    when(actuatorClient.apply(ChaosCommandAction.DISABLE, null))
        .thenAnswer(
            invocation -> {
              disabled.countDown();
              return new ChaosActuatorClient.ApplyResult(true, null, 200);
            });
    ChaosExpiryGuard guard = new ChaosExpiryGuard(actuatorClient, mock(ChaosMetrics.class));
    ChaosCommandMessage message =
        new ChaosCommandMessage(
            UUID.randomUUID(),
            "test",
            "orders",
            ChaosCommandAction.ENABLE,
            null,
            Instant.now().plusMillis(50),
            "tester",
            "expiry-test");

    guard.afterSuccessfulApply(message);

    assertTrue(disabled.await(2, TimeUnit.SECONDS));
    guard.shutdown();
  }

  @Test
  void disableCancelsPendingExpiryTask() {
    ScheduledTask task = scheduledTask();
    ChaosExpiryGuard guard =
        new ChaosExpiryGuard(
            mock(ChaosActuatorClient.class), mock(ChaosMetrics.class), task.scheduler);
    Instant expiresAt = Instant.now().plusSeconds(60);

    guard.afterSuccessfulApply(message(ChaosCommandAction.ENABLE, expiresAt));
    guard.afterSuccessfulApply(message(ChaosCommandAction.DISABLE, null));
    task.runnable.get().run();

    verify(task.future).cancel(false);
    verify(task.scheduler, never()).shutdownNow();
  }

  @Test
  void configureDoesNotScheduleDisable() {
    ScheduledTask task = scheduledTask();
    ChaosExpiryGuard guard =
        new ChaosExpiryGuard(
            mock(ChaosActuatorClient.class), mock(ChaosMetrics.class), task.scheduler);

    guard.afterSuccessfulApply(message(ChaosCommandAction.CONFIGURE, null));

    verify(task.scheduler, never()).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
  }

  @Test
  void failedAutomaticDisableIsRecorded() {
    ScheduledTask task = scheduledTask();
    ChaosActuatorClient actuatorClient = mock(ChaosActuatorClient.class);
    ChaosMetrics metrics = mock(ChaosMetrics.class);
    when(actuatorClient.apply(ChaosCommandAction.DISABLE, null))
        .thenReturn(new ChaosActuatorClient.ApplyResult(false, "disable", 500));
    ChaosExpiryGuard guard = new ChaosExpiryGuard(actuatorClient, metrics, task.scheduler);

    guard.afterSuccessfulApply(message(ChaosCommandAction.ENABLE, Instant.now().plusSeconds(60)));
    task.runnable.get().run();

    verify(metrics).automaticDisable(false);
  }

  private static ScheduledTask scheduledTask() {
    ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
    @SuppressWarnings("unchecked")
    ScheduledFuture<Object> future = mock(ScheduledFuture.class);
    AtomicReference<Runnable> runnable = new AtomicReference<>();
    when(scheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
        .thenAnswer(
            invocation -> {
              runnable.set(invocation.getArgument(0));
              return future;
            });
    return new ScheduledTask(scheduler, future, runnable);
  }

  private static ChaosCommandMessage message(ChaosCommandAction action, Instant expiresAt) {
    return new ChaosCommandMessage(
        UUID.randomUUID(), "test", "orders", action, null, expiresAt, "tester", "expiry-test");
  }

  private record ScheduledTask(
      ScheduledExecutorService scheduler,
      ScheduledFuture<?> future,
      AtomicReference<Runnable> runnable) {}
}
