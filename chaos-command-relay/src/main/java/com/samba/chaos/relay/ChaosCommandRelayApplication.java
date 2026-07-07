package com.samba.chaos.relay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(ChaosRelayProperties.class)
@EnableScheduling
public class ChaosCommandRelayApplication {

  public static void main(String[] args) {
    SpringApplication.run(ChaosCommandRelayApplication.class, args);
  }
}
