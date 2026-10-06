package com.samba.chaos.relay.console;

import com.samba.chaos.relay.service.TargetInstancesResolver;
import java.util.List;
import java.util.Map;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads {@code /actuator/chaosmonkey/status} and {@code /assaults} on each UP instance.
 *
 * <p>No UP instances yields an unconfigured snapshot. A transport failure yields unreachable.
 */
@Service
public class ChaosMonkeyActuatorProbe {

  private final TargetInstancesResolver instancesResolver;
  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  /**
   * Creates the probe.
   *
   * @param instancesResolver UP instances of the target application
   * @param restClient HTTP client
   * @param objectMapper formats the actuator JSON for the snapshot
   */
  public ChaosMonkeyActuatorProbe(
      TargetInstancesResolver instancesResolver, RestClient restClient, ObjectMapper objectMapper) {
    this.instancesResolver = instancesResolver;
    this.restClient = restClient;
    this.objectMapper = objectMapper;
  }

  /**
   * Reads the live Chaos Monkey state.
   *
   * @param applicationName Eureka application name
   * @return unconfigured, unreachable, or a snapshot that includes the first successful JSON
   */
  public ChaosMonkeyRuntimeSnapshot probe(String applicationName) {
    List<ServiceInstance> instances = instancesResolver.resolveUp(applicationName);
    if (instances.isEmpty()) {
      return ChaosMonkeyRuntimeSnapshot.unconfigured();
    }

    Boolean enabled = null;
    String statusText = null;
    String assaultsText = null;
    String lastError = null;
    for (ServiceInstance instance : instances) {
      String baseUrl = instance.getUri() + "/actuator/chaosmonkey";
      try {
        JsonNode status = restClient.get().uri(baseUrl + "/status").retrieve().body(JsonNode.class);
        JsonNode assaults =
            restClient.get().uri(baseUrl + "/assaults").retrieve().body(JsonNode.class);
        boolean instanceEnabled = status != null && status.path("enabled").asBoolean(false);
        enabled = Boolean.TRUE.equals(enabled) || instanceEnabled;
        if (statusText == null) {
          statusText = format(status);
          assaultsText = format(assaults);
        }
      } catch (RestClientException ex) {
        lastError = ex.getMessage();
      }
    }
    if (enabled == null) {
      return ChaosMonkeyRuntimeSnapshot.unreachable(lastError);
    }
    return ChaosMonkeyRuntimeSnapshot.ok(enabled, statusText, assaultsText);
  }

  private String format(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    Object value = objectMapper.treeToValue(node, Object.class);
    if (value instanceof Map<?, ?> map && map.isEmpty()) {
      return null;
    }
    return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
  }
}
