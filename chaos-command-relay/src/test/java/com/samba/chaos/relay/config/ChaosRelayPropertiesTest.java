package com.samba.chaos.relay.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChaosRelayPropertiesTest {

  @Test
  void storesCommandTtlAndVerifyUiUrl() {
    ChaosRelayProperties properties = new ChaosRelayProperties();

    properties.setCommandTtlHours(2);
    properties.setVerifyUiUrl("http://localhost:18000");
    properties.setAllowedTargetApplications(List.of("chaos-poc-demo"));

    assertThat(properties.getCommandTtlHours()).isEqualTo(2);
    assertThat(properties.getVerifyUiUrl()).isEqualTo("http://localhost:18000");
    assertThat(properties.getAllowedTargetApplications()).containsExactly("chaos-poc-demo");
  }
}
