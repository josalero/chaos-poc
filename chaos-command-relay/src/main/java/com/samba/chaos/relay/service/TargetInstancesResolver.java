package com.samba.chaos.relay.service;

import com.netflix.appinfo.InstanceInfo;
import java.util.List;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.netflix.eureka.EurekaServiceInstance;
import org.springframework.stereotype.Component;

/**
 * UP instances for a target application.
 *
 * <p>Instances without a Eureka status are kept, so a non-Eureka discovery client still receives
 * commands. A status other than {@code UP} is dropped.
 */
@Component
public class TargetInstancesResolver {

  private final DiscoveryClient discoveryClient;

  /**
   * Creates the resolver.
   *
   * @param discoveryClient Eureka, or the test discovery client
   */
  public TargetInstancesResolver(DiscoveryClient discoveryClient) {
    this.discoveryClient = discoveryClient;
  }

  /**
   * Instances that should receive the next command.
   *
   * @param applicationName Eureka application name
   * @return UP instances, or every instance when status is absent
   */
  public List<ServiceInstance> resolveUp(String applicationName) {
    return discoveryClient.getInstances(applicationName).stream()
        .filter(TargetInstancesResolver::isUp)
        .toList();
  }

  private static boolean isUp(ServiceInstance instance) {
    return !(instance instanceof EurekaServiceInstance eureka)
        || eureka.getInstanceInfo().getStatus() == InstanceInfo.InstanceStatus.UP;
  }
}
