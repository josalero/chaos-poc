package com.samba.chaos.relay.console;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samba.chaos.relay.ChaosRelayProperties;
import java.io.IOException;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class ChaosMonkeyActuatorProbe {

  private final ChaosRelayProperties properties;
  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;

  public ChaosMonkeyActuatorProbe(
      ChaosRelayProperties properties, RestTemplate restTemplate, ObjectMapper objectMapper) {
    this.properties = properties;
    this.restTemplate = restTemplate;
    this.objectMapper = objectMapper;
  }

  public ChaosMonkeyRuntimeSnapshot probe(String applicationName) {
    String baseUrl = properties.getActuatorBaseUrls().get(applicationName);
    if (baseUrl == null || baseUrl.isBlank()) {
      return ChaosMonkeyRuntimeSnapshot.unconfigured();
    }

    try {
      JsonNode status = restTemplate.getForObject(baseUrl + "/status", JsonNode.class);
      JsonNode assaults = restTemplate.getForObject(baseUrl + "/assaults", JsonNode.class);
      boolean enabled = status != null && status.path("enabled").asBoolean(false);
      return ChaosMonkeyRuntimeSnapshot.ok(
          enabled, format(status), format(assaults));
    } catch (RestClientException ex) {
      return ChaosMonkeyRuntimeSnapshot.unreachable(ex.getMessage());
    }
  }

  @SuppressWarnings("unchecked")
  private String format(JsonNode node) throws RestClientException {
    if (node == null || node.isNull()) {
      return null;
    }
    try {
      Object value = objectMapper.treeToValue(node, Object.class);
      if (value instanceof Map<?, ?> map && map.isEmpty()) {
        return null;
      }
      return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to format Chaos Monkey actuator response", ex);
    }
  }
}
