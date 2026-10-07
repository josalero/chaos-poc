package com.samba.chaos.config;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Applies idle Chaos Monkey defaults when {@code samba.chaos.command.enabled} is true.
 *
 * <p>Chaos Monkey 4 loads only for the {@code chaos-monkey} profile. This processor adds that
 * profile so the host does not declare it. A production profile or environment fails startup before
 * the profile is added. Defaults are added last, so a value the service already set wins. The
 * actuator exposure list is merged so {@code chaosmonkey} is present without dropping the host's
 * existing endpoints.
 */
public final class ChaosEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

  static final String ENABLED = "samba.chaos.command.enabled";
  static final String ENVIRONMENT = "samba.chaos.command.environment";
  static final String EXPOSURE = "management.endpoints.web.exposure.include";
  static final String CHAOS_ENDPOINT = "chaosmonkey";

  private static final String REFUSED =
      "samba.chaos.command.enabled=true is refused for a production profile or environment";

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    if (!"true".equalsIgnoreCase(environment.getProperty(ENABLED))) {
      return;
    }
    if (environment.matchesProfiles("production", "prod")
        || "production".equalsIgnoreCase(environment.getProperty(ENVIRONMENT))) {
      throw new IllegalStateException(REFUSED);
    }
    environment.addActiveProfile("chaos-monkey");
    Map<String, Object> defaults = new HashMap<>();
    defaults.put("chaos.monkey.enabled", "false");
    defaults.put("chaos.monkey.watcher.service", "true");
    defaults.put("chaos.monkey.watcher.rest-controller", "false");
    defaults.put("chaos.monkey.assaults.level", "1");
    defaults.put("management.endpoint.chaosmonkey.access", "unrestricted");
    environment
        .getPropertySources()
        .addLast(new MapPropertySource("samba-chaos-defaults", defaults));

    String current = environment.getProperty(EXPOSURE, "health");
    if (!containsEndpoint(current, CHAOS_ENDPOINT)) {
      Map<String, Object> exposure = Map.of(EXPOSURE, current + "," + CHAOS_ENDPOINT);
      environment
          .getPropertySources()
          .addFirst(new MapPropertySource("samba-chaos-exposure", exposure));
    }
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }

  private static boolean containsEndpoint(String current, String endpoint) {
    return Arrays.stream(current.split(",")).map(String::trim).anyMatch(endpoint::equals);
  }
}
