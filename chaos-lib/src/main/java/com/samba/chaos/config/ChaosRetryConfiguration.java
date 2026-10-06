package com.samba.chaos.config;

import com.samba.chaos.exception.TransientActuatorException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.backoff.FixedBackOff;
import org.springframework.web.client.ResourceAccessException;

/** Retry policy for actuator calls that fail with 503, 504, or a connection error. */
@Configuration
@ConditionalOnProperty(prefix = "samba.chaos.command", name = "enabled", havingValue = "true")
public class ChaosRetryConfiguration {

  @Bean
  RetryTemplate chaosActuatorRetryTemplate(ChaosProperties properties) {
    long retries = Math.max(0, properties.getMaxApplyAttempts() - 1L);
    RetryPolicy policy =
        RetryPolicy.builder()
            .backOff(new FixedBackOff(properties.getApplyBackoffMs(), retries))
            .includes(TransientActuatorException.class, ResourceAccessException.class)
            .build();
    return new RetryTemplate(policy);
  }
}
