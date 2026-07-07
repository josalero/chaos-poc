package com.samba.chaos.relay.console;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samba.chaos.relay.ChaosRelayProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

class ChaosMonkeyActuatorProbeTest {

  @Test
  void probe_returnsUnconfiguredWhenNoActuatorUrl() {
    ChaosRelayProperties properties = new ChaosRelayProperties();
    ChaosMonkeyActuatorProbe probe =
        new ChaosMonkeyActuatorProbe(properties, new RestTemplate(), new ObjectMapper());

    ChaosMonkeyRuntimeSnapshot snapshot = probe.probe("chaos-poc-demo");

    assertThat(snapshot.configured()).isFalse();
    assertThat(snapshot.reachable()).isFalse();
  }

  @Test
  void probe_returnsLiveStatusAndAssaults() {
    ChaosRelayProperties properties = new ChaosRelayProperties();
    properties.setActuatorBaseUrls(
        Map.of("chaos-poc-demo", "http://demo/actuator/chaosmonkey"));

    RestTemplate restTemplate = org.mockito.Mockito.mock(RestTemplate.class);
    when(restTemplate.getForObject(
            eq("http://demo/actuator/chaosmonkey/status"), eq(com.fasterxml.jackson.databind.JsonNode.class)))
        .thenReturn(new ObjectMapper().createObjectNode().put("enabled", true));
    when(restTemplate.getForObject(
            eq("http://demo/actuator/chaosmonkey/assaults"), eq(com.fasterxml.jackson.databind.JsonNode.class)))
        .thenReturn(
            new ObjectMapper().createObjectNode().put("latencyActive", true).put("level", 1));

    ChaosMonkeyActuatorProbe probe =
        new ChaosMonkeyActuatorProbe(properties, restTemplate, new ObjectMapper());

    ChaosMonkeyRuntimeSnapshot snapshot = probe.probe("chaos-poc-demo");

    assertThat(snapshot.reachable()).isTrue();
    assertThat(snapshot.enabled()).isTrue();
    assertThat(snapshot.statusJson()).contains("\"enabled\" : true");
    assertThat(snapshot.assaultsJson()).contains("latencyActive");
  }
}
