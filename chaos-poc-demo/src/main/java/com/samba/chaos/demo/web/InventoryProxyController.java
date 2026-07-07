package com.samba.chaos.demo.web;

import com.samba.chaos.demo.client.InventoryClient;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryProxyController {

  private final InventoryClient inventoryClient;

  public InventoryProxyController(InventoryClient inventoryClient) {
    this.inventoryClient = inventoryClient;
  }

  @GetMapping("/{sku}")
  public Map<String, Object> getStock(@PathVariable String sku) {
    return inventoryClient.getStock(sku);
  }
}
