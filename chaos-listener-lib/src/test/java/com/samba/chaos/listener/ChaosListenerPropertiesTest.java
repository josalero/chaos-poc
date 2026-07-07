package com.samba.chaos.listener;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChaosListenerPropertiesTest {

  @Test
  void providesJavaDefaultsForListenerTopologyWithoutYamlDefaults() {
    ChaosListenerProperties properties = new ChaosListenerProperties();

    assertThat(properties.getEnvironment()).isEqualTo("test");
    assertThat(properties.getCommandsExchange()).isEqualTo("chaos.commands.test");
    assertThat(properties.getResultsQueue()).isEqualTo("chaos.command-results");
    assertThat(properties.getMaxApplyAttempts()).isEqualTo(3);
    assertThat(properties.getApplyBackoffMs()).isEqualTo(200);
    assertThat(properties.getMaxPublishAttempts()).isEqualTo(3);
    assertThat(properties.getPublishBackoffMs()).isEqualTo(100);
    assertThat(properties.getRabbitmq().getHost()).isNull();
  }
}
