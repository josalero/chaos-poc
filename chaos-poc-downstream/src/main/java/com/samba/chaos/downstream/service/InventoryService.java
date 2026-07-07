package com.samba.chaos.downstream.service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InventoryService {

  private final Set<String> reserved = ConcurrentHashMap.newKeySet();

  public Map<String, Object> getStock(String sku) {
    if ("NOT-FOUND".equalsIgnoreCase(sku)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "SKU not found");
    }
    if ("FORBIDDEN".equalsIgnoreCase(sku)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SKU access denied");
    }
    return Map.of("sku", sku, "available", 42, "warehouse", "WH-01");
  }

  public Map<String, Object> reserve(String sku) {
    if ("CONFLICT".equalsIgnoreCase(sku) || !reserved.add(sku.toUpperCase())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "SKU already reserved");
    }
    return Map.of("sku", sku, "status", "RESERVED");
  }

  public void clearReservations() {
    reserved.clear();
  }
}
