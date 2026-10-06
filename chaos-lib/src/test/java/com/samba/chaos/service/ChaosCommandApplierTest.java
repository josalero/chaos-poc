package com.samba.chaos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.samba.chaos.command.ChaosCommandAction;
import com.samba.chaos.command.ChaosCommandMessage;
import com.samba.chaos.command.ChaosCommandResult;
import com.samba.chaos.command.InstanceOutcome;
import com.samba.chaos.config.ChaosProperties;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class ChaosCommandApplierTest {

  private ChaosActuatorClient actuatorClient;
  private ChaosExpiryGuard expiryGuard;
  private ChaosMetrics metrics;
  private ChaosCommandApplier applier;

  @BeforeEach
  void setUp() {
    ChaosProperties properties = new ChaosProperties();
    properties.setApplicationName("orders");
    properties.setEnvironment("test");
    properties.setPodName("pod-a");
    actuatorClient = mock(ChaosActuatorClient.class);
    expiryGuard = mock(ChaosExpiryGuard.class);
    metrics = mock(ChaosMetrics.class);
    applier = new ChaosCommandApplier("orders", properties, actuatorClient, expiryGuard, metrics);
  }

  @Test
  void duplicateCommandReturnsCachedResultWithoutReapplyingAssault() {
    ChaosCommandMessage message = enableMessage(Instant.now().plusSeconds(60));
    when(actuatorClient.apply(message.action(), message.assault()))
        .thenReturn(new ChaosActuatorClient.ApplyResult(true, null, 200));

    Logger logger = (Logger) LoggerFactory.getLogger(ChaosCommandApplier.class);
    Level previous = logger.getLevel();
    logger.setLevel(Level.DEBUG);
    ChaosCommandResult first;
    ChaosCommandResult second;
    try {
      first = applier.apply(message);
      second = applier.apply(message);
    } finally {
      logger.setLevel(previous);
    }

    assertThat(second).isEqualTo(first);
    assertThat(first.outcome()).isEqualTo(InstanceOutcome.SUCCESS);
    verify(actuatorClient, times(1)).apply(message.action(), message.assault());
    verify(expiryGuard).afterSuccessfulApply(message);
    verify(metrics).duplicateResultRepublished();
  }

  @Test
  void expiredCommandIsRejected() {
    ChaosCommandResult result = applier.apply(enableMessage(Instant.now().minusSeconds(1)));

    assertThat(result.outcome()).isEqualTo(InstanceOutcome.REJECTED);
    assertThat(result.failedStep()).isEqualTo("command expired");
    verify(actuatorClient, times(0))
        .apply(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void enableWithoutExpiryIsRejected() {
    ChaosCommandResult result = applier.apply(enableMessage(null));

    assertThat(result.outcome()).isEqualTo(InstanceOutcome.REJECTED);
    assertThat(result.failedStep()).isEqualTo("enable command requires expiresAt");
    verify(actuatorClient, times(0))
        .apply(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void environmentMismatchIsRejected() {
    ChaosCommandMessage message =
        new ChaosCommandMessage(
            UUID.randomUUID(),
            "production",
            "orders",
            ChaosCommandAction.ENABLE,
            null,
            Instant.now().plusSeconds(30),
            "tester",
            "mismatch");

    ChaosCommandResult result = applier.apply(message);

    assertThat(result.outcome()).isEqualTo(InstanceOutcome.REJECTED);
    assertThat(result.failedStep()).isEqualTo("environment mismatch");
  }

  @Test
  void applicationMismatchIsRejected() {
    ChaosCommandMessage message =
        new ChaosCommandMessage(
            UUID.randomUUID(),
            "test",
            "billing",
            ChaosCommandAction.ENABLE,
            null,
            Instant.now().plusSeconds(30),
            "tester",
            "mismatch");

    ChaosCommandResult result = applier.apply(message);

    assertThat(result.outcome()).isEqualTo(InstanceOutcome.REJECTED);
    assertThat(result.failedStep()).isEqualTo("application mismatch");
  }

  private ChaosCommandMessage enableMessage(Instant expiresAt) {
    return new ChaosCommandMessage(
        UUID.randomUUID(),
        "test",
        "orders",
        ChaosCommandAction.ENABLE,
        null,
        expiresAt,
        "tester",
        "test-run");
  }
}
