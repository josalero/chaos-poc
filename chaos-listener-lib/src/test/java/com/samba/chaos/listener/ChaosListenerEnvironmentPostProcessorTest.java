package com.samba.chaos.listener;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class ChaosListenerEnvironmentPostProcessorTest {

  private final ChaosListenerEnvironmentPostProcessor processor =
      new ChaosListenerEnvironmentPostProcessor();

  @Test
  void whenListenerEnabled_bridgesServiceRabbitMqToSpring() {
    MockEnvironment environment = baseEnvironment("local,chaos-monkey");

    processor.postProcessEnvironment(environment, new SpringApplication());

    assertThat(environment.getProperty("samba.chaos.command-listener.rabbitmq.host"))
        .isEqualTo("localhost");
    assertThat(environment.getProperty("spring.rabbitmq.host")).isEqualTo("localhost");
    assertThat(environment.getProperty("spring.rabbitmq.port")).isEqualTo("5672");
    assertThat(environment.getProperty("spring.rabbitmq.username")).isEqualTo("chaos");
  }

  @Test
  void whenServiceProvidesDockerRabbitMqHost_bridgesToSpring() {
    MockEnvironment environment = baseEnvironment("docker,test");
    environment.setProperty("samba.chaos.command-listener.rabbitmq.host", "rabbitmq");

    processor.postProcessEnvironment(environment, new SpringApplication());

    assertThat(environment.getProperty("samba.chaos.command-listener.rabbitmq.host"))
        .isEqualTo("rabbitmq");
    assertThat(environment.getProperty("spring.rabbitmq.host")).isEqualTo("rabbitmq");
    assertThat(environment.getProperty("spring.rabbitmq.username")).isEqualTo("chaos");
  }

  @Test
  void whenListenerDisabled_doesNotBridgeRabbitMq() {
    MockEnvironment environment = new MockEnvironment();
    environment.setProperty("samba.chaos.command-listener.enabled", "false");

    processor.postProcessEnvironment(environment, new SpringApplication());

    assertThat(environment.getProperty("spring.rabbitmq.host")).isNull();
  }

  @Test
  void whenServiceOverridesRabbitMqHost_respectsOverride() {
    MockEnvironment environment = baseEnvironment("docker,test");
    environment.setProperty("spring.rabbitmq.host", "custom-broker");

    processor.postProcessEnvironment(environment, new SpringApplication());

    assertThat(environment.getProperty("spring.rabbitmq.host")).isEqualTo("custom-broker");
  }

  @Test
  void whenLegacyCommandListenerEnabled_mirrorsSambaPrefix() {
    MockEnvironment environment = new MockEnvironment();
    environment.setProperty("chaos.command-listener.enabled", "true");
    environment.setProperty("spring.application.name", "chaos-poc-demo");
    environment.setProperty("spring.profiles.active", "local");
    withServiceRabbitMq(environment);

    processor.postProcessEnvironment(environment, new SpringApplication());

    assertThat(environment.getProperty("samba.chaos.command-listener.enabled")).isEqualTo("true");
  }

  private static MockEnvironment baseEnvironment(String profiles) {
    MockEnvironment environment = new MockEnvironment();
    environment.setProperty("samba.chaos.command-listener.enabled", "true");
    environment.setProperty("spring.application.name", "chaos-poc-demo");
    environment.setProperty("spring.profiles.active", profiles);
    withServiceRabbitMq(environment);
    return environment;
  }

  private static void withServiceRabbitMq(MockEnvironment environment) {
    environment.setProperty("samba.chaos.command-listener.rabbitmq.host", "localhost");
    environment.setProperty("samba.chaos.command-listener.rabbitmq.port", "5672");
    environment.setProperty("samba.chaos.command-listener.rabbitmq.username", "chaos");
    environment.setProperty("samba.chaos.command-listener.rabbitmq.password", "chaos");
  }
}
