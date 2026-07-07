package com.samba.chaos.listener;

import com.samba.chaos.listener.exception.TransientActuatorException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.ResourceAccessException;

@Configuration
@EnableRetry
@ConditionalOnProperty(prefix = "samba.chaos.command-listener", name = "enabled", havingValue = "true")
public class ChaosListenerRetryConfiguration {

  @Bean
  RetryTemplate chaosActuatorRetryTemplate(ChaosListenerProperties properties) {
    return RetryTemplate.builder()
        .maxAttempts(properties.getMaxApplyAttempts())
        .fixedBackoff(properties.getApplyBackoffMs())
        .retryOn(TransientActuatorException.class)
        .retryOn(ResourceAccessException.class)
        .traversingCauses()
        .build();
  }
}
