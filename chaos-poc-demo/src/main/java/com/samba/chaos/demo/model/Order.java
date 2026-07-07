package com.samba.chaos.demo.model;

import java.time.Instant;
import java.util.UUID;

public record Order(
    UUID id, String sku, int quantity, OrderStatus status, Instant createdAt) {

  public Order withStatus(OrderStatus newStatus) {
    return new Order(id, sku, quantity, newStatus, createdAt);
  }
}
