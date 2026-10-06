package com.samba.chaos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.samba.chaos.command.ChaosCommandAction;
import com.samba.chaos.command.ChaosCommandMessage;
import com.samba.chaos.command.ChaosCommandResult;
import com.samba.chaos.command.InstanceOutcome;
import com.samba.chaos.config.ChaosProperties;
import com.samba.chaos.service.ChaosActuatorClient;
import com.samba.chaos.service.ChaosCommandApplier;
import com.samba.chaos.service.ChaosExpiryGuard;
import com.samba.chaos.service.ChaosMetrics;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

class ChaosAutoConfigurationTest {

  @Test
  void usesSpringApplicationNameWhenChaosPropertyIsBlank() {
    ChaosProperties properties = new ChaosProperties();
    properties.setApplicationName(" ");
    properties.setEnvironment("test");
    properties.setPodName("pod-a");
    Environment environment = mock(Environment.class);
    when(environment.getRequiredProperty("spring.application.name")).thenReturn("from-env");
    ChaosActuatorClient actuatorClient = mock(ChaosActuatorClient.class);
    when(actuatorClient.apply(ChaosCommandAction.ENABLE, null))
        .thenReturn(new ChaosActuatorClient.ApplyResult(true, null, 200));

    ChaosCommandApplier applier =
        new ChaosAutoConfiguration()
            .chaosCommandApplier(
                properties,
                environment,
                actuatorClient,
                mock(ChaosExpiryGuard.class),
                mock(ChaosMetrics.class));

    ChaosCommandResult result =
        applier.apply(
            new ChaosCommandMessage(
                UUID.randomUUID(),
                "test",
                "from-env",
                ChaosCommandAction.ENABLE,
                null,
                Instant.now().plusSeconds(30),
                "tester",
                "auto-config"));

    assertThat(result.outcome()).isEqualTo(InstanceOutcome.SUCCESS);
  }
}
