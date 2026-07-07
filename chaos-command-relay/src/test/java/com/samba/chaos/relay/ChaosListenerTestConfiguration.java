package com.samba.chaos.relay;

import com.samba.chaos.listener.ChaosListenerProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class ChaosListenerTestConfiguration {

  @Bean
  @Primary
  ChaosListenerProperties chaosListenerProperties(@Value("${local.server.port}") int port) {
    ChaosListenerProperties properties = new ChaosListenerProperties();
    properties.setEnabled(true);
    properties.setEnvironment("test");
    properties.setApplicationName("chaos-poc-demo");
    properties.setPodName("test-pod-1");
    properties.setActuatorBaseUrl("http://127.0.0.1:" + port + "/actuator/chaosmonkey");
    properties.setCommandsExchange("chaos.commands.test");
    properties.setResultsQueue("chaos.command-results");
    return properties;
  }
}
