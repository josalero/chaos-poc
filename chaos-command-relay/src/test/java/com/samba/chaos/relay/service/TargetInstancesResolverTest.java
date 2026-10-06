package com.samba.chaos.relay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.appinfo.InstanceInfo;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.netflix.eureka.EurekaServiceInstance;

class TargetInstancesResolverTest {

  @Test
  void resolveUpCountsOnlyUpInstances() {
    DiscoveryClient discoveryClient = mock(DiscoveryClient.class);
    InstanceInfo up =
        InstanceInfo.Builder.newBuilder()
            .setAppName("DEMO")
            .setStatus(InstanceInfo.InstanceStatus.UP)
            .build();
    InstanceInfo down =
        InstanceInfo.Builder.newBuilder()
            .setAppName("DEMO")
            .setStatus(InstanceInfo.InstanceStatus.DOWN)
            .build();
    when(discoveryClient.getInstances("chaos-poc-demo"))
        .thenReturn(
            List.of(
                new EurekaServiceInstance(up),
                new EurekaServiceInstance(down),
                new DefaultServiceInstance("plain", "chaos-poc-demo", "10.0.0.2", 8080, false)));

    List<?> instances = new TargetInstancesResolver(discoveryClient).resolveUp("chaos-poc-demo");

    assertThat(instances).hasSize(2);
  }
}
