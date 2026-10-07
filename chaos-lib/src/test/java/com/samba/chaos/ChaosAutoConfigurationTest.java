package com.samba.chaos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.retry.RetryTemplate;

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

  @Test
  void usesTheConfiguredApplicationName() {
    ChaosProperties properties = new ChaosProperties();
    properties.setApplicationName("orders");
    properties.setEnvironment("test");
    Environment environment = mock(Environment.class);
    ChaosActuatorClient actuatorClient = mock(ChaosActuatorClient.class);
    when(actuatorClient.apply(ChaosCommandAction.DISABLE, null))
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
                "orders",
                ChaosCommandAction.DISABLE,
                null,
                null,
                "tester",
                "auto-config"));

    assertThat(result.outcome()).isEqualTo(InstanceOutcome.SUCCESS);
  }

  @Test
  void derivesTheActuatorUrlFromTheConfiguredServerPort() {
    ChaosProperties properties = new ChaosProperties();
    Environment environment = mock(Environment.class);
    when(environment.getProperty("server.port")).thenReturn("8081");

    ChaosActuatorClient client =
        new ChaosAutoConfiguration()
            .chaosActuatorClient(properties, environment, mock(RetryTemplate.class));

    assertThat(properties.getActuatorBaseUrl())
        .isEqualTo("http://127.0.0.1:8081/actuator/chaosmonkey");
    assertThat(client).isNotNull();
  }

  @Test
  void keepsAnExplicitActuatorUrl() {
    ChaosProperties properties = new ChaosProperties();
    properties.setActuatorBaseUrl("http://127.0.0.1:9090/actuator/chaosmonkey");
    Environment environment = mock(Environment.class);
    when(environment.getProperty("server.port")).thenReturn("8081");

    assertThat(ChaosAutoConfiguration.actuatorBaseUrl(properties, environment))
        .isEqualTo("http://127.0.0.1:9090/actuator/chaosmonkey");
  }

  @Test
  void prefersTheManagementPortOverTheServerPort() {
    ChaosProperties properties = new ChaosProperties();
    Environment environment = mock(Environment.class);
    when(environment.getProperty("local.management.port")).thenReturn("8081");
    when(environment.getProperty("server.port")).thenReturn("8080");

    assertThat(ChaosAutoConfiguration.actuatorBaseUrl(properties, environment))
        .isEqualTo("http://127.0.0.1:8081/actuator/chaosmonkey");
  }

  @Test
  void usesPort8080WhenNoPortIsConfigured() {
    ChaosProperties properties = new ChaosProperties();
    properties.setActuatorBaseUrl(" ");
    Environment environment = mock(Environment.class);

    assertThat(ChaosAutoConfiguration.actuatorBaseUrl(properties, environment))
        .isEqualTo("http://127.0.0.1:8080/actuator/chaosmonkey");
  }

  @Test
  void refusesToStartInProduction() {
    ChaosProperties properties = new ChaosProperties();
    properties.setEnvironment("production");
    Environment environment = mock(Environment.class);
    ApplicationRunner runner =
        new ChaosAutoConfiguration().rejectChaosInProduction(environment, properties);

    assertThatThrownBy(() -> runner.run(null)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void allowsStartupOutsideProduction() throws Exception {
    ChaosProperties properties = new ChaosProperties();
    Environment environment = mock(Environment.class);
    when(environment.matchesProfiles("production", "prod")).thenReturn(false);
    ApplicationRunner runner =
        new ChaosAutoConfiguration().rejectChaosInProduction(environment, properties);

    runner.run(null);
  }

  @Test
  void registersExpiryGuardAndMetrics() {
    ChaosAutoConfiguration configuration = new ChaosAutoConfiguration();
    ChaosMetrics metrics = configuration.chaosMetrics(new SimpleMeterRegistry());
    ChaosExpiryGuard guard =
        configuration.chaosExpiryGuard(mock(ChaosActuatorClient.class), metrics);

    assertThat(metrics).isNotNull();
    assertThat(guard).isNotNull();
  }
}
