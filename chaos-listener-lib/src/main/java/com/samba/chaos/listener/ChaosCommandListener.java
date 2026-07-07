package com.samba.chaos.listener;

import com.samba.chaos.listener.message.ChaosCommandMessage;
import com.samba.chaos.listener.message.ChaosCommandResult;
import com.samba.chaos.listener.message.InstanceOutcome;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ChaosCommandListener {

  private static final Logger log = LoggerFactory.getLogger(ChaosCommandListener.class);

  private final ChaosListenerProperties properties;
  private final ChaosActuatorClient actuatorClient;
  private final ChaosCommandResultPublisher resultPublisher;
  private final Set<String> processedCommandIds = ConcurrentHashMap.newKeySet();

  public ChaosCommandListener(
      ChaosListenerProperties properties,
      ChaosActuatorClient actuatorClient,
      ChaosCommandResultPublisher resultPublisher) {
    this.properties = properties;
    this.actuatorClient = actuatorClient;
    this.resultPublisher = resultPublisher;
  }

  @RabbitListener(
      bindings =
          @QueueBinding(
              value = @Queue(value = "", durable = "false", exclusive = "true", autoDelete = "true"),
              exchange =
                  @Exchange(
                      name = "${samba.chaos.command-listener.commands-exchange:chaos.commands.test}",
                      type = "fanout")))
  public void onCommand(ChaosCommandMessage message) {
    if (!shouldApply(message)) {
      return;
    }

    if (!processedCommandIds.add(message.commandId().toString())) {
      log.debug("Ignoring duplicate commandId={}", message.commandId());
      return;
    }

    ChaosActuatorClient.ApplyResult result = actuatorClient.apply(message.action(), message.assault());
    InstanceOutcome outcome =
        result.succeeded() ? InstanceOutcome.SUCCESS : InstanceOutcome.ACTUATOR_ERROR;

    resultPublisher.publish(
        new ChaosCommandResult(
            message.commandId(),
            message.targetApplication(),
            properties.getPodName(),
            outcome,
            result.failedStep(),
            result.httpStatus(),
            Instant.now()));

    log.info(
        "Chaos command {} applied on pod {} outcome={}",
        message.commandId(),
        properties.getPodName(),
        outcome);
  }

  private boolean shouldApply(ChaosCommandMessage message) {
    if (!properties.getEnvironment().equals(message.environment())) {
      return false;
    }
    if (!properties.getApplicationName().equals(message.targetApplication())) {
      return false;
    }
    if (message.expiresAt() != null && message.expiresAt().isBefore(Instant.now())) {
      return false;
    }
    return true;
  }
}
