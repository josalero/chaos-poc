package com.samba.chaos.downstream.web;

import com.samba.chaos.downstream.service.InventoryService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Member. */
@RestController
@RequestMapping("/v1")
public class InventoryController {

  private final InventoryService inventoryService;

  /** Inventory Controller. */
  public InventoryController(InventoryService inventoryService) {
    this.inventoryService = inventoryService;
  }

  /** Get Mapping. */
  @GetMapping("/inventory/{sku}")
  public Map<String, Object> getStock(@PathVariable String sku) {
    return inventoryService.getStock(sku);
  }

  /** Post Mapping. */
  @PostMapping("/inventory/reserve")
  public ResponseEntity<Map<String, Object>> reserve(@RequestBody ReserveRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(inventoryService.reserve(request.sku()));
  }

  /** Get Mapping. */
  @GetMapping("/auth/{resource}")
  public Map<String, String> checkAccess(@PathVariable String resource) {
    if ("restricted".equalsIgnoreCase(resource)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
    }
    return Map.of("resource", resource, "access", "granted");
  }

  /** Reserve Request. */
  public record ReserveRequest(String sku, int quantity) {}
}
