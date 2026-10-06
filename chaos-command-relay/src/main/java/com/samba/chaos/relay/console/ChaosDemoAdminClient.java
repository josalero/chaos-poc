package com.samba.chaos.relay.console;

import com.samba.chaos.relay.service.TargetInstancesResolver;
import java.util.List;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Clears demo orders by posting {@code /api/v1/admin/reset} to every UP instance.
 *
 * <p>This does not change the Chaos Monkey assault.
 */
@Service
public class ChaosDemoAdminClient {

  private final TargetInstancesResolver instancesResolver;
  private final RestClient restClient;

  /**
   * Creates the client.
   *
   * @param instancesResolver UP instances of the target application
   * @param restClient HTTP client
   */
  public ChaosDemoAdminClient(TargetInstancesResolver instancesResolver, RestClient restClient) {
    this.instancesResolver = instancesResolver;
    this.restClient = restClient;
  }

  /**
   * Posts the admin reset to each UP instance.
   *
   * @param applicationName Eureka application name
   * @return false when no instance is UP
   * @throws IllegalStateException when an instance rejects or drops the call
   */
  public boolean resetDemoData(String applicationName) {
    List<ServiceInstance> instances = instancesResolver.resolveUp(applicationName);
    if (instances.isEmpty()) {
      return false;
    }

    try {
      for (ServiceInstance instance : instances) {
        String url = instance.getUri() + "/api/v1/admin/reset";
        restClient.post().uri(url).retrieve().toBodilessEntity();
      }
      return true;
    } catch (RestClientException ex) {
      throw new IllegalStateException(
          "Demo data reset failed for " + applicationName + ": " + ex.getMessage(), ex);
    }
  }
}
