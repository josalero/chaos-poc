package com.samba.chaos.demo.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.samba.chaos.demo.client.AuthorizationClient;
import com.samba.chaos.demo.client.InventoryClient;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

class DownstreamClientConfigTest {

  @Test
  void rewritesTheServiceHostToTheChosenInstance() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          byte[] body = "{\"sku\":\"ABC\",\"access\":\"granted\"}".getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
          }
        });
    server.start();
    try {
      int port = server.getAddress().getPort();
      LoadBalancerClient loadBalancer = mock(LoadBalancerClient.class);
      when(loadBalancer.choose(anyString()))
          .thenReturn(
              new DefaultServiceInstance(
                  "pod-a", "chaos-poc-downstream", "127.0.0.1", port, false));
      DownstreamClientConfig config = new DownstreamClientConfig();
      HttpServiceProxyFactory factory = config.downstreamProxyFactory(loadBalancer);
      InventoryClient inventory = config.inventoryClient(factory);
      AuthorizationClient authorization = config.authorizationClient(factory);

      assertThat(inventory.getStock("ABC")).containsEntry("sku", "ABC");
      assertThat(authorization.checkAccess("orders")).containsEntry("access", "granted");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void failsWhenDiscoveryHasNoInstance() {
    LoadBalancerClient loadBalancer = mock(LoadBalancerClient.class);
    when(loadBalancer.choose(anyString())).thenReturn(null);
    InventoryClient inventory =
        new DownstreamClientConfig()
            .inventoryClient(new DownstreamClientConfig().downstreamProxyFactory(loadBalancer));

    assertThatThrownBy(() -> inventory.getStock("ABC"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("chaos-poc-downstream");
  }
}
