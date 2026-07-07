package com.samba.chaos.demo.service;

import com.samba.chaos.demo.client.InventoryClient;
import com.samba.chaos.demo.client.InventoryClient.ReserveRequest;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class InventoryGateway {

  private final InventoryClient inventoryClient;

  public InventoryGateway(InventoryClient inventoryClient) {
    this.inventoryClient = inventoryClient;
  }

  public Map<String, Object> getStock(String sku) {
    return inventoryClient.getStock(sku);
  }

  public Map<String, Object> reserve(ReserveRequest request) {
    return inventoryClient.reserve(request);
  }
}
