package com.samba.chaos.demo.service;

import com.samba.chaos.demo.client.InventoryClient.ReserveRequest;
import com.samba.chaos.demo.exception.DuplicateOrderException;
import com.samba.chaos.demo.model.Order;
import com.samba.chaos.demo.model.OrderStatus;
import com.samba.chaos.demo.repository.OrderRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

  private final OrderRepository orderRepository;
  private final InventoryGateway inventoryGateway;
  private final AuthorizationGateway authorizationGateway;

  public OrderService(
      OrderRepository orderRepository,
      InventoryGateway inventoryGateway,
      AuthorizationGateway authorizationGateway) {
    this.orderRepository = orderRepository;
    this.inventoryGateway = inventoryGateway;
    this.authorizationGateway = authorizationGateway;
  }

  public Order placeOrder(String sku, int quantity) {
    if (orderRepository.existsBySku(sku)) {
      throw new DuplicateOrderException(sku);
    }

    Map<String, Object> stock = inventoryGateway.getStock(sku);

    Order order =
        new Order(UUID.randomUUID(), sku, quantity, OrderStatus.CREATED, Instant.now());
    orderRepository.save(order);

    return order;
  }

  public Order submitOrder(UUID orderId) {
    Order order =
        orderRepository
            .findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found"));

    authorizationGateway.checkAccess("orders");

    inventoryGateway.reserve(new ReserveRequest(order.sku(), order.quantity()));

    Order submitted = order.withStatus(OrderStatus.SUBMITTED);
    orderRepository.save(submitted);
    return submitted;
  }

  public Order getOrder(UUID orderId) {
    return orderRepository
        .findById(orderId)
        .orElseThrow(() -> new IllegalArgumentException("Order not found"));
  }
}
