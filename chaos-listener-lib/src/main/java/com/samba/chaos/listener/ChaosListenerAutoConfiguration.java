package com.samba.chaos.listener;

import com.samba.chaos.listener.message.ChaosJsonMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.retry.support.RetryTemplate;

@AutoConfiguration
@Profile({"test", "chaos-monkey"})
@ConditionalOnProperty(prefix = "samba.chaos.command-listener", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ChaosListenerProperties.class)
@ComponentScan(basePackageClasses = ChaosListenerAutoConfiguration.class)
@Import(ChaosListenerRetryConfiguration.class)
public class ChaosListenerAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(MessageConverter.class)
  MessageConverter chaosJacksonMessageConverter() {
    return new Jackson2JsonMessageConverter(ChaosJsonMapper.create());
  }

  @Bean
  ChaosActuatorClient chaosActuatorClient(
      ChaosListenerProperties properties, RetryTemplate chaosActuatorRetryTemplate) {
    return new ChaosActuatorClient(properties, chaosActuatorRetryTemplate);
  }
}
