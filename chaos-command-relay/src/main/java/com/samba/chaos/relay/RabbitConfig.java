package com.samba.chaos.relay;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import com.samba.chaos.listener.message.ChaosJsonMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

  @Bean
  FanoutExchange chaosCommandsExchange(ChaosRelayProperties properties) {
    return new FanoutExchange(properties.getRabbit().getCommandsExchange(), true, false);
  }

  @Bean
  Queue chaosCommandResultsQueue(ChaosRelayProperties properties) {
    return new Queue(properties.getRabbit().getResultsQueue(), true);
  }

  @Bean
  MessageConverter jacksonMessageConverter() {
    return new Jackson2JsonMessageConverter(ChaosJsonMapper.create());
  }
}
