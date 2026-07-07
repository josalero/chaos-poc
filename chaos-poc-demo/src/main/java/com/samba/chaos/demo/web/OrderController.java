package com.samba.chaos.demo.web;

import com.samba.chaos.demo.model.Order;
import com.samba.chaos.demo.service.OrderService;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

  private final OrderService orderService;

  public OrderController(OrderService orderService) {
    this.orderService = orderService;
  }

  @PostMapping
  public ResponseEntity<Order> create(@RequestBody CreateOrderRequest request) {
    Order order = orderService.placeOrder(request.sku(), request.quantity());
    return ResponseEntity.status(HttpStatus.CREATED).body(order);
  }

  @PostMapping("/{orderId}/submit")
  public ResponseEntity<Order> submit(@PathVariable UUID orderId) {
    return ResponseEntity.ok(orderService.submitOrder(orderId));
  }

  @GetMapping("/{orderId}")
  public ResponseEntity<Order> get(@PathVariable UUID orderId) {
    return ResponseEntity.ok(orderService.getOrder(orderId));
  }

  @GetMapping("/health-check")
  public Map<String, String> healthCheck() {
    return Map.of("status", "ok");
  }

  public record CreateOrderRequest(String sku, int quantity) {}
}
