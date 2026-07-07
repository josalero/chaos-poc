package com.samba.chaos.relay;

import org.springframework.stereotype.Component;

@Component
public class ExpectedInstancesResolver {

  private final ChaosRelayProperties properties;

  public ExpectedInstancesResolver(ChaosRelayProperties properties) {
    this.properties = properties;
  }

  public int resolve(String targetApplication) {
    return properties
        .getExpectedInstancesFallback()
        .getOrDefault(targetApplication, 1);
  }
}
