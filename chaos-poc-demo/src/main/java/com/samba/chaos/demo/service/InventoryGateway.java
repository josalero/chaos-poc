package com.samba.chaos.demo.service;

import com.samba.chaos.demo.client.InventoryClient;
import com.samba.chaos.demo.client.InventoryClient.ReserveRequest;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Member. */
@Service
public class InventoryGateway {

  private final InventoryClient inventoryClient;

  /** Inventory Gateway. */
  public InventoryGateway(InventoryClient inventoryClient) {
    this.inventoryClient = inventoryClient;
  }

  /** Get Stock. */
  public Map<String, Object> getStock(String sku) {
    return inventoryClient.getStock(sku);
  }

  /** Reserve. */
  public Map<String, Object> reserve(ReserveRequest request) {
    return inventoryClient.reserve(request);
  }
}
