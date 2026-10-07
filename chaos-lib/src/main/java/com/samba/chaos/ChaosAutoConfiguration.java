package com.samba.chaos;

import com.samba.chaos.config.ChaosProperties;
import com.samba.chaos.config.ChaosRetryConfiguration;
import com.samba.chaos.config.ChaosSecurityConfiguration;
import com.samba.chaos.service.ChaosActuatorClient;
import com.samba.chaos.service.ChaosCommandApplier;
import com.samba.chaos.service.ChaosExpiryGuard;
import com.samba.chaos.service.ChaosMetrics;
import com.samba.chaos.web.ChaosCommandEndpoint;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.core.retry.RetryTemplate;

/**
 * Activates the chaos command endpoint when {@code samba.chaos.command.enabled} is true.
 *
 * <p>Chaos Monkey must be on the classpath. The relay depends on this starter for the JSON contract
 * and leaves the flag unset, so this configuration does not start there.
 */
@AutoConfiguration
@ConditionalOnClass(
    name = "de.codecentric.spring.boot.chaos.monkey.configuration.ChaosMonkeyConfiguration")
@ConditionalOnProperty(prefix = "samba.chaos.command", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ChaosProperties.class)
@Import({
  ChaosRetryConfiguration.class,
  ChaosSecurityConfiguration.class,
  ChaosCommandEndpoint.class
})
public class ChaosAutoConfiguration {

  /**
   * Posts assaults to the loopback actuator. An explicit base URL on {@code properties} wins.
   *
   * @param properties bound command settings
   * @param environment host environment, used when the actuator URL is omitted
   * @param chaosActuatorRetryTemplate retry policy for transient actuator failures
   * @return the actuator client
   */
  @Bean
  ChaosActuatorClient chaosActuatorClient(
      ChaosProperties properties,
      Environment environment,
      RetryTemplate chaosActuatorRetryTemplate) {
    properties.setActuatorBaseUrl(actuatorBaseUrl(properties, environment));
    return new ChaosActuatorClient(properties, chaosActuatorRetryTemplate);
  }

  /**
   * Applies commands for this application's Eureka name.
   *
   * @param properties bound command settings
   * @param environment host environment
   * @param chaosActuatorClient loopback actuator client
   * @param expiryGuard local lease enforcer
   * @param metrics command counters
   * @return the applier
   */
  @Bean
  ChaosCommandApplier chaosCommandApplier(
      ChaosProperties properties,
      Environment environment,
      ChaosActuatorClient chaosActuatorClient,
      ChaosExpiryGuard expiryGuard,
      ChaosMetrics metrics) {
    String applicationName =
        Optional.ofNullable(properties.getApplicationName())
            .filter(name -> !name.isBlank())
            .orElseGet(() -> environment.getRequiredProperty("spring.application.name"));
    return new ChaosCommandApplier(
        applicationName, properties, chaosActuatorClient, expiryGuard, metrics);
  }

  /**
   * Schedules disable when an enable lease expires.
   *
   * @param chaosActuatorClient loopback actuator client
   * @param metrics command counters
   * @return the expiry guard
   */
  @Bean
  ChaosExpiryGuard chaosExpiryGuard(ChaosActuatorClient chaosActuatorClient, ChaosMetrics metrics) {
    return new ChaosExpiryGuard(chaosActuatorClient, metrics);
  }

  /**
   * Records command outcomes.
   *
   * @param meterRegistry host meter registry
   * @return the metrics bean
   */
  @Bean
  ChaosMetrics chaosMetrics(MeterRegistry meterRegistry) {
    return new ChaosMetrics(meterRegistry);
  }

  /**
   * Fails startup when the flag is on in production. The environment post-processor checks first.
   *
   * @param environment host environment
   * @param properties bound command settings
   * @return a runner that rejects production
   */
  @Bean
  ApplicationRunner rejectChaosInProduction(Environment environment, ChaosProperties properties) {
    return args -> {
      boolean productionProfile = environment.matchesProfiles("production", "prod");
      boolean productionEnvironment = "production".equalsIgnoreCase(properties.getEnvironment());
      if (productionProfile || productionEnvironment) {
        throw new IllegalStateException(
            "samba.chaos.command.enabled=true is refused when the profile is production or prod, "
                + "or when samba.chaos.command.environment is production");
      }
    };
  }

  /**
   * Loopback Chaos Monkey URL. An explicit property wins. Otherwise the management port, then the
   * bound server port, then the configured {@code server.port}.
   *
   * @param properties bound command settings
   * @param environment host environment
   * @return actuator base URL without a trailing slash
   */
  static String actuatorBaseUrl(ChaosProperties properties, Environment environment) {
    if (properties.getActuatorBaseUrl() != null && !properties.getActuatorBaseUrl().isBlank()) {
      return properties.getActuatorBaseUrl();
    }
    String port =
        firstNonBlank(
            environment.getProperty("local.management.port"),
            environment.getProperty("management.server.port"),
            environment.getProperty("local.server.port"),
            environment.getProperty("server.port"),
            "8080");
    return "http://127.0.0.1:" + port + "/actuator/chaosmonkey";
  }

  private static String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return "8080";
  }
}
