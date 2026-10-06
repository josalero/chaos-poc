package com.samba.chaos.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(
    properties = {
      "spring.cloud.config.enabled=false",
      "spring.cloud.config.import-check.enabled=false",
      "eureka.client.enabled=false"
    })
class GatewayRouteTest {

  @Autowired private RouteDefinitionLocator routes;

  @Test
  void eachPathPrefixRoutesToTheMatchingService() {
    Map<String, String> byId =
        routes
            .getRouteDefinitions()
            .collectMap(RouteDefinition::getId, route -> route.getUri().toString())
            .block();

    assertThat(byId)
        .containsEntry("demo-api", "lb://chaos-poc-demo")
        .containsEntry("downstream-api", "lb://chaos-poc-downstream")
        .containsEntry("relay-api", "lb://chaos-command-relay")
        .containsEntry("relay-console", "http://chaos-command-relay-ui:80")
        .containsEntry("ui", "http://chaos-poc-ui:80");
  }
}
