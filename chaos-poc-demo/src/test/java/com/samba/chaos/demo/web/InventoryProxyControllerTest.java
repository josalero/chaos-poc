package com.samba.chaos.demo.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.samba.chaos.demo.client.InventoryClient;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InventoryProxyControllerTest {

  @Test
  void delegatesStockLookup() {
    InventoryClient client = mock(InventoryClient.class);
    when(client.getStock("ABC")).thenReturn(Map.of("sku", "ABC"));

    assertThat(new InventoryProxyController(client).getStock("ABC")).containsEntry("sku", "ABC");
  }
}
