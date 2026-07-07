package com.samba.chaos.demo.web;

import com.samba.chaos.demo.repository.OrderRepository;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminResetController {

  private final OrderRepository orderRepository;

  public AdminResetController(OrderRepository orderRepository) {
    this.orderRepository = orderRepository;
  }

  @PostMapping("/reset")
  public ResponseEntity<Map<String, String>> resetDemoData() {
    orderRepository.clearAll();
    return ResponseEntity.ok(Map.of("status", "OK", "message", "Demo order data cleared"));
  }
}
