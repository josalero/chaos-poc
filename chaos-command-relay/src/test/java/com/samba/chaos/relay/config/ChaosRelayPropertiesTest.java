package com.samba.chaos.relay.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChaosRelayPropertiesTest {

  @Test
  void storesVerifyUiUrlAndAllowlist() {
    ChaosRelayProperties properties = new ChaosRelayProperties();

    properties.setVerifyUiUrl("http://localhost:18000");
    properties.setAllowedTargetApplications(List.of("chaos-poc-demo"));

    assertThat(properties.getVerifyUiUrl()).isEqualTo("http://localhost:18000");
    assertThat(properties.getAllowedTargetApplications()).containsExactly("chaos-poc-demo");
  }
}
