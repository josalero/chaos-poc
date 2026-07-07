package com.samba.chaos.relay;

import com.samba.chaos.listener.message.ChaosCommandResult;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ChaosCommandResultConsumer {

  private final ChaosCommandStore commandStore;

  public ChaosCommandResultConsumer(ChaosCommandStore commandStore) {
    this.commandStore = commandStore;
  }

  @RabbitListener(queues = "${chaos.relay.rabbit.results-queue}")
  public void onResult(ChaosCommandResult result) {
    commandStore.addResult(result);
  }
}
