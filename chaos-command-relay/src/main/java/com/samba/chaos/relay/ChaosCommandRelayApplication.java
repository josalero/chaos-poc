package com.samba.chaos.relay;

import com.samba.chaos.relay.client.ChaosCommandClient;
import com.samba.chaos.relay.config.ChaosRelayProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Command relay. Discovers UP instances and posts each command to them.
 *
 * <p>Scheduling removes stored commands past {@code chaos.relay.command-ttl-hours}.
 */
@SpringBootApplication
@EnableConfigurationProperties(ChaosRelayProperties.class)
@EnableFeignClients(basePackageClasses = ChaosCommandClient.class)
@EnableScheduling
public class ChaosCommandRelayApplication {

  /**
   * Starts the relay.
   *
   * @param args application arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(ChaosCommandRelayApplication.class, args);
  }
}
