package com.samba.chaos.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.samba.chaos.demo.client.AuthorizationClient;
import com.samba.chaos.demo.client.InventoryClient;
import com.samba.chaos.demo.client.InventoryClient.ReserveRequest;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DownstreamGatewayTest {

  @Test
  void gatewaysDelegateToTheHttpClients() {
    InventoryClient inventoryClient = mock(InventoryClient.class);
    AuthorizationClient authorizationClient = mock(AuthorizationClient.class);
    ReserveRequest request = new ReserveRequest("ABC", 1);
    when(inventoryClient.getStock("ABC")).thenReturn(Map.of("sku", "ABC"));
    when(inventoryClient.reserve(request)).thenReturn(Map.of("status", "RESERVED"));
    when(authorizationClient.checkAccess("orders")).thenReturn(Map.of("access", "granted"));

    assertThat(new InventoryGateway(inventoryClient).getStock("ABC")).containsEntry("sku", "ABC");
    assertThat(new InventoryGateway(inventoryClient).reserve(request))
        .containsEntry("status", "RESERVED");
    assertThat(new AuthorizationGateway(authorizationClient).checkAccess("orders"))
        .containsEntry("access", "granted");
  }
}
