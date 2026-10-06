package com.samba.chaos;

import com.samba.chaos.config.ChaosProperties;
import com.samba.chaos.config.ChaosRetryConfiguration;
import com.samba.chaos.config.ChaosSecurityConfiguration;
import com.samba.chaos.service.ChaosActuatorClient;
import com.samba.chaos.service.ChaosCommandApplier;
import com.samba.chaos.service.ChaosExpiryGuard;
import com.samba.chaos.service.ChaosMetrics;
import java.util.Optional;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.retry.RetryTemplate;

/** Activates chaos-lib when the profile and {@code samba.chaos.command.enabled} allow it. */
@AutoConfiguration
@Profile({"test", "chaos-monkey"})
@ConditionalOnProperty(prefix = "samba.chaos.command", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ChaosProperties.class)
@ComponentScan(basePackageClasses = ChaosAutoConfiguration.class)
@Import({ChaosRetryConfiguration.class, ChaosSecurityConfiguration.class})
public class ChaosAutoConfiguration {

  @Bean
  ChaosActuatorClient chaosActuatorClient(
      ChaosProperties properties, RetryTemplate chaosActuatorRetryTemplate) {
    return new ChaosActuatorClient(properties, chaosActuatorRetryTemplate);
  }

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
}
