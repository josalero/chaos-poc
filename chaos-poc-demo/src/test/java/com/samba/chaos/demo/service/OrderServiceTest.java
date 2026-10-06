package com.samba.chaos.demo.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.samba.chaos.demo.exception.DuplicateOrderException;
import com.samba.chaos.demo.model.Order;
import com.samba.chaos.demo.model.OrderStatus;
import com.samba.chaos.demo.repository.OrderRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderServiceTest {

  private OrderRepository orderRepository;
  private InventoryGateway inventoryGateway;
  private AuthorizationGateway authorizationGateway;
  private OrderService orderService;

  @BeforeEach
  void setUp() {
    orderRepository = mock(OrderRepository.class);
    inventoryGateway = mock(InventoryGateway.class);
    authorizationGateway = mock(AuthorizationGateway.class);
    orderService = new OrderService(orderRepository, inventoryGateway, authorizationGateway);
  }

  @Test
  void placeOrder_doesNotCheckAuthorization() {
    when(inventoryGateway.getStock("WIDGET-1"))
        .thenReturn(Map.of("sku", "WIDGET-1", "available", 10));

    orderService.placeOrder("WIDGET-1", 1);

    verifyNoInteractions(authorizationGateway);
  }

  @Test
  void submitOrder_checksOrderAuthorization() {
    UUID orderId = UUID.randomUUID();
    Order order = new Order(orderId, "WIDGET-1", 1, OrderStatus.CREATED, Instant.now());
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

    orderService.submitOrder(orderId);

    verify(authorizationGateway).checkAccess("orders");
  }

  @Test
  void placeOrder_rejectsDuplicateSku() {
    when(orderRepository.existsBySku("WIDGET-1")).thenReturn(true);

    assertThatThrownBy(() -> orderService.placeOrder("WIDGET-1", 1))
        .isInstanceOf(DuplicateOrderException.class);
  }

  @Test
  void getOrder_rejectsUnknownId() {
    UUID orderId = UUID.randomUUID();
    when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.getOrder(orderId))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
