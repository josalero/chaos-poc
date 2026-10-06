package com.samba.chaos.demo.service;

import com.samba.chaos.demo.client.InventoryClient.ReserveRequest;
import com.samba.chaos.demo.exception.DuplicateOrderException;
import com.samba.chaos.demo.model.Order;
import com.samba.chaos.demo.model.OrderStatus;
import com.samba.chaos.demo.repository.OrderRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Member. */
@Service
public class OrderService {

  private final OrderRepository orderRepository;
  private final InventoryGateway inventoryGateway;
  private final AuthorizationGateway authorizationGateway;

  /** Order Service. */
  public OrderService(
      OrderRepository orderRepository,
      InventoryGateway inventoryGateway,
      AuthorizationGateway authorizationGateway) {
    this.orderRepository = orderRepository;
    this.inventoryGateway = inventoryGateway;
    this.authorizationGateway = authorizationGateway;
  }

  /** Place Order. */
  public Order placeOrder(String sku, int quantity) {
    if (orderRepository.existsBySku(sku)) {
      throw new DuplicateOrderException(sku);
    }

    Map<String, Object> stock = inventoryGateway.getStock(sku);
    Objects.requireNonNull(stock.get("sku"), "Inventory response missing sku");

    Order order = new Order(UUID.randomUUID(), sku, quantity, OrderStatus.CREATED, Instant.now());
    orderRepository.save(order);

    return order;
  }

  /** Submit Order. */
  public Order submitOrder(UUID orderId) {
    Order order =
        orderRepository
            .findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found"));

    authorizeOrders();

    inventoryGateway.reserve(new ReserveRequest(order.sku(), order.quantity()));

    Order submitted = order.withStatus(OrderStatus.SUBMITTED);
    orderRepository.save(submitted);
    return submitted;
  }

  /** Get Order. */
  public Order getOrder(UUID orderId) {
    return orderRepository
        .findById(orderId)
        .orElseThrow(() -> new IllegalArgumentException("Order not found"));
  }

  private void authorizeOrders() {
    authorizationGateway.checkAccess("orders");
  }
}
