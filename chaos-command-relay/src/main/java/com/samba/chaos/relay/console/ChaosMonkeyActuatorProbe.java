package com.samba.chaos.relay.console;

import com.samba.chaos.relay.service.TargetInstancesResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads {@code /actuator/chaosmonkey/status} and {@code /assaults} on each UP instance.
 *
 * <p>Calls run in parallel on {@code chaosProbeExecutor}. No UP instances yields an unconfigured
 * snapshot. A transport failure on every instance yields unreachable.
 */
@Service
public class ChaosMonkeyActuatorProbe {

  private final TargetInstancesResolver instancesResolver;
  private final RestClient restClient;
  private final ObjectMapper objectMapper;
  private final TaskExecutor chaosProbeExecutor;

  /**
   * Creates the probe.
   *
   * @param instancesResolver UP instances of the target application
   * @param restClient HTTP client
   * @param objectMapper formats the actuator JSON for the snapshot
   * @param chaosProbeExecutor executor for per-instance actuator calls
   */
  public ChaosMonkeyActuatorProbe(
      TargetInstancesResolver instancesResolver,
      RestClient restClient,
      ObjectMapper objectMapper,
      @Qualifier("chaosProbeExecutor") TaskExecutor chaosProbeExecutor) {
    this.instancesResolver = instancesResolver;
    this.restClient = restClient;
    this.objectMapper = objectMapper;
    this.chaosProbeExecutor = chaosProbeExecutor;
  }

  /**
   * Reads the live Chaos Monkey state, including assault JSON.
   *
   * @param applicationName Eureka application name
   * @return unconfigured, unreachable, or a snapshot with one row per UP instance
   */
  public ChaosMonkeyRuntimeSnapshot probe(String applicationName) {
    List<ServiceInstance> instances = instancesResolver.resolveUp(applicationName);
    if (instances.isEmpty()) {
      return ChaosMonkeyRuntimeSnapshot.unconfigured();
    }

    List<InstanceRead> reads = readAll(instances, true);
    List<ChaosInstanceActuatorSnapshot> snapshots = new ArrayList<>();
    Boolean enabled = null;
    String statusText = null;
    String assaultsText = null;
    String lastError = null;
    for (InstanceRead read : reads) {
      if (read.error() != null) {
        lastError = read.error();
        snapshots.add(
            new ChaosInstanceActuatorSnapshot(read.instanceId(), null, null, null, read.error()));
        continue;
      }
      boolean instanceEnabled =
          read.status() != null && read.status().path("enabled").asBoolean(false);
      enabled = Boolean.TRUE.equals(enabled) || instanceEnabled;
      String status = format(read.status());
      String assaults = format(read.assaults());
      if (statusText == null) {
        statusText = status;
        assaultsText = assaults;
      }
      snapshots.add(
          new ChaosInstanceActuatorSnapshot(
              read.instanceId(), instanceEnabled, status, assaults, null));
    }
    if (enabled == null) {
      return ChaosMonkeyRuntimeSnapshot.unreachable(lastError, snapshots);
    }
    return ChaosMonkeyRuntimeSnapshot.ok(enabled, statusText, assaultsText, snapshots);
  }

  /**
   * Reads only {@code /status} and counts how many instances answered.
   *
   * @param applicationName Eureka application name
   * @return enabled when any pod reports on, null when none answered
   */
  public EnabledRead readEnabled(String applicationName) {
    List<ServiceInstance> instances = instancesResolver.resolveUp(applicationName);
    if (instances.isEmpty()) {
      return new EnabledRead(null, 0, 0);
    }
    List<InstanceRead> reads = readAll(instances, false);
    Boolean enabled = null;
    int reachable = 0;
    for (InstanceRead read : reads) {
      if (read.error() != null || read.status() == null) {
        continue;
      }
      reachable++;
      boolean instanceEnabled = read.status().path("enabled").asBoolean(false);
      enabled = Boolean.TRUE.equals(enabled) || instanceEnabled;
    }
    return new EnabledRead(enabled, reachable, instances.size());
  }

  private List<InstanceRead> readAll(List<ServiceInstance> instances, boolean includeAssaults) {
    List<CompletableFuture<InstanceRead>> futures = new ArrayList<>();
    for (ServiceInstance instance : instances) {
      futures.add(
          CompletableFuture.supplyAsync(
              () -> readInstance(instance, includeAssaults), chaosProbeExecutor));
    }
    return futures.stream().map(CompletableFuture::join).toList();
  }

  private InstanceRead readInstance(ServiceInstance instance, boolean includeAssaults) {
    String instanceId = instanceKey(instance);
    String baseUrl = instance.getUri() + "/actuator/chaosmonkey";
    try {
      JsonNode status = restClient.get().uri(baseUrl + "/status").retrieve().body(JsonNode.class);
      JsonNode assaults = null;
      if (includeAssaults) {
        assaults = restClient.get().uri(baseUrl + "/assaults").retrieve().body(JsonNode.class);
      }
      return new InstanceRead(instanceId, status, assaults, null);
    } catch (RestClientException ex) {
      return new InstanceRead(instanceId, null, null, ex.getMessage());
    }
  }

  private String instanceKey(ServiceInstance instance) {
    String instanceId = instance.getInstanceId();
    if (instanceId != null && !instanceId.isBlank()) {
      return instanceId;
    }
    return instance.getHost();
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

  /** Chaos Monkey on/off for one application, without assault JSON. */
  public record EnabledRead(Boolean enabled, int reachableInstances, int upInstances) {}

  private record InstanceRead(
      String instanceId, JsonNode status, JsonNode assaults, String error) {}
}
