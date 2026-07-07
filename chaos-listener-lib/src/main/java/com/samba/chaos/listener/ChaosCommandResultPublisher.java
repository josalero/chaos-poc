package com.samba.chaos.listener;

import com.samba.chaos.listener.message.ChaosCommandResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

@Component
public class ChaosCommandResultPublisher {

  private static final Logger log = LoggerFactory.getLogger(ChaosCommandResultPublisher.class);

  private final RabbitTemplate rabbitTemplate;
  private final ChaosListenerProperties properties;

  public ChaosCommandResultPublisher(
      RabbitTemplate rabbitTemplate, ChaosListenerProperties properties) {
    this.rabbitTemplate = rabbitTemplate;
    this.properties = properties;
  }

  @Retryable(
      retryFor = {AmqpException.class, ResourceAccessException.class},
      maxAttemptsExpression = "${samba.chaos.command-listener.max-publish-attempts:3}",
      backoff = @Backoff(delayExpression = "${samba.chaos.command-listener.publish-backoff-ms:100}"))
  public void publish(ChaosCommandResult result) {
    rabbitTemplate.convertAndSend(properties.getResultsQueue(), result);
  }

  @Recover
  public void recoverPublish(Throwable cause, ChaosCommandResult result) {
    log.error(
        "Failed to publish chaos result after retries commandId={} pod={}",
        result.commandId(),
        result.podName(),
        cause);
  }
}
