package com.samba.chaos.downstream.web;

import com.samba.chaos.downstream.service.InventoryService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Member. */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminResetController {

  private final InventoryService inventoryService;

  /** Admin Reset Controller. */
  public AdminResetController(InventoryService inventoryService) {
    this.inventoryService = inventoryService;
  }

  /** Post Mapping. */
  @PostMapping("/reset")
  public ResponseEntity<Map<String, String>> resetDownstreamData() {
    inventoryService.clearReservations();
    return ResponseEntity.ok(Map.of("status", "OK", "message", "Downstream reservations cleared"));
  }
}
