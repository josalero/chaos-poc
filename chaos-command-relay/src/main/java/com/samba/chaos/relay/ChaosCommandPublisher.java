package com.samba.chaos.relay;

import com.samba.chaos.listener.message.ChaosCommandMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class ChaosCommandPublisher {

  private final RabbitTemplate rabbitTemplate;
  private final ChaosRelayProperties properties;

  public ChaosCommandPublisher(RabbitTemplate rabbitTemplate, ChaosRelayProperties properties) {
    this.rabbitTemplate = rabbitTemplate;
    this.properties = properties;
  }

  public void publish(ChaosCommandMessage message) {
    rabbitTemplate.convertAndSend(properties.getRabbit().getCommandsExchange(), "", message);
  }
}
