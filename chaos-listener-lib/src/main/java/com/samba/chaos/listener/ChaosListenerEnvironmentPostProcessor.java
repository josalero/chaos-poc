package com.samba.chaos.listener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

@Order(Ordered.LOWEST_PRECEDENCE)
public class ChaosListenerEnvironmentPostProcessor implements EnvironmentPostProcessor {

  private static final String SAMBA_COMMAND_LISTENER_PREFIX = "samba.chaos.command-listener.";
  private static final String COMMAND_LISTENER_PREFIX = "chaos.command-listener.";
  private static final String LISTENER_PREFIX = "chaos.listener.";

  private static final List<String> COMMAND_LISTENER_SUFFIXES =
      List.of(
          "enabled",
          "environment",
          "application-name",
          "pod-name",
          "actuator-base-url",
          "commands-exchange",
          "results-queue",
          "max-apply-attempts",
          "apply-backoff-ms",
          "max-publish-attempts",
          "publish-backoff-ms",
          "rabbitmq.host",
          "rabbitmq.port",
          "rabbitmq.username",
          "rabbitmq.password");

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    applyPropertyAliases(environment);
    if (!isListenerEnabled(environment)) {
      return;
    }
    bridgeCommandListenerRabbitMqToSpring(environment);
  }

  private void applyPropertyAliases(ConfigurableEnvironment environment) {
    Map<String, Object> aliases = new HashMap<>();

    mirrorEnabledFlags(environment, aliases);
    for (String suffix : COMMAND_LISTENER_SUFFIXES) {
      if ("enabled".equals(suffix)) {
        continue;
      }
      mirrorProperty(
          environment, aliases, SAMBA_COMMAND_LISTENER_PREFIX + suffix, COMMAND_LISTENER_PREFIX + suffix);
      mirrorProperty(
          environment, aliases, COMMAND_LISTENER_PREFIX + suffix, LISTENER_PREFIX + suffix);
    }

    if (!environment.containsProperty(SAMBA_COMMAND_LISTENER_PREFIX + "application-name")
        && !environment.containsProperty(COMMAND_LISTENER_PREFIX + "application-name")
        && !environment.containsProperty(LISTENER_PREFIX + "application-name")) {
      String appName = environment.getProperty("spring.application.name");
      if (appName != null && !appName.isBlank()) {
        aliases.put(SAMBA_COMMAND_LISTENER_PREFIX + "application-name", appName);
        aliases.put(COMMAND_LISTENER_PREFIX + "application-name", appName);
        aliases.put(LISTENER_PREFIX + "application-name", appName);
      }
    }

    if (!aliases.isEmpty()) {
      environment
          .getPropertySources()
          .addFirst(new MapPropertySource("chaos-listener-property-aliases", aliases));
    }
  }

  private static void mirrorEnabledFlags(
      ConfigurableEnvironment environment, Map<String, Object> aliases) {
    boolean samba =
        Boolean.parseBoolean(
            environment.getProperty(SAMBA_COMMAND_LISTENER_PREFIX + "enabled", "false"));
    boolean commandListener =
        Boolean.parseBoolean(environment.getProperty(COMMAND_LISTENER_PREFIX + "enabled", "false"));
    boolean listener =
        Boolean.parseBoolean(environment.getProperty(LISTENER_PREFIX + "enabled", "false"));
    boolean enabled = samba || commandListener || listener;

    if (enabled) {
      if (!environment.containsProperty(SAMBA_COMMAND_LISTENER_PREFIX + "enabled")) {
        aliases.put(SAMBA_COMMAND_LISTENER_PREFIX + "enabled", true);
      }
      if (!environment.containsProperty(COMMAND_LISTENER_PREFIX + "enabled")) {
        aliases.put(COMMAND_LISTENER_PREFIX + "enabled", true);
      }
      if (!environment.containsProperty(LISTENER_PREFIX + "enabled")) {
        aliases.put(LISTENER_PREFIX + "enabled", true);
      }
    }
  }

  private static void mirrorProperty(
      ConfigurableEnvironment environment,
      Map<String, Object> aliases,
      String canonicalKey,
      String legacyKey) {
    if (!environment.containsProperty(canonicalKey) && environment.containsProperty(legacyKey)) {
      aliases.put(canonicalKey, environment.getProperty(legacyKey));
    }
    if (!environment.containsProperty(legacyKey) && environment.containsProperty(canonicalKey)) {
      aliases.put(legacyKey, environment.getProperty(canonicalKey));
    }
  }

  private boolean isListenerEnabled(ConfigurableEnvironment environment) {
    return Boolean.parseBoolean(
            environment.getProperty(SAMBA_COMMAND_LISTENER_PREFIX + "enabled", "false"))
        || Boolean.parseBoolean(environment.getProperty(COMMAND_LISTENER_PREFIX + "enabled", "false"))
        || Boolean.parseBoolean(environment.getProperty(LISTENER_PREFIX + "enabled", "false"));
  }

  private void bridgeCommandListenerRabbitMqToSpring(ConfigurableEnvironment environment) {
    bridgeRabbitMqProperty(environment, "host");
    bridgeRabbitMqProperty(environment, "port");
    bridgeRabbitMqProperty(environment, "username");
    bridgeRabbitMqProperty(environment, "password");
  }

  private void bridgeRabbitMqProperty(ConfigurableEnvironment environment, String key) {
    String springKey = "spring.rabbitmq." + key;
    if (environment.containsProperty(springKey)) {
      return;
    }
    String value = resolveCommandListenerProperty(environment, "rabbitmq." + key);
    if (value == null) {
      return;
    }
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "chaos-listener-rabbitmq-bridge-" + key, Map.of(springKey, value)));
  }

  private static String resolveCommandListenerProperty(
      ConfigurableEnvironment environment, String suffix) {
    for (String prefix :
        List.of(SAMBA_COMMAND_LISTENER_PREFIX, COMMAND_LISTENER_PREFIX, LISTENER_PREFIX)) {
      String key = prefix + suffix;
      if (environment.containsProperty(key)) {
        return environment.getProperty(key);
      }
    }
    return null;
  }
}
