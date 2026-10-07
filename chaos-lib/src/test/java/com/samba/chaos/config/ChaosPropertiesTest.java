package com.samba.chaos.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChaosPropertiesTest {

  @Test
  void providesLocalActuatorDefaults() {
    ChaosProperties properties = new ChaosProperties();

    assertThat(properties.getEnvironment()).isEqualTo("test");
    assertThat(properties.getPodName()).isEqualTo("local");
    assertThat(properties.getActuatorBaseUrl()).isNull();
    assertThat(properties.getMaxApplyAttempts()).isEqualTo(3);
    assertThat(properties.getApplyBackoffMs()).isEqualTo(200);
    assertThat(properties.isEnabled()).isFalse();

    properties.setApplicationName("orders");
    properties.setApplyBackoffMs(50);
    assertThat(properties.getApplicationName()).isEqualTo("orders");
    assertThat(properties.getApplyBackoffMs()).isEqualTo(50);
  }
}
