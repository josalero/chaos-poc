package com.samba.chaos.demo.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.samba.chaos.demo.model.Order;
import com.samba.chaos.demo.model.OrderStatus;
import com.samba.chaos.demo.service.OrderService;
import com.samba.chaos.demo.web.OrderController.CreateOrderRequest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class OrderControllerTest {

  @Test
  void createsSubmitsAndReadsOrders() {
    OrderService orderService = mock(OrderService.class);
    OrderController controller = new OrderController(orderService);
    UUID orderId = UUID.randomUUID();
    Order order = new Order(orderId, "WIDGET-1", 2, OrderStatus.CREATED, Instant.now());
    CreateOrderRequest request = new CreateOrderRequest("WIDGET-1", 2);
    when(orderService.placeOrder(request.sku(), request.quantity())).thenReturn(order);
    when(orderService.submitOrder(orderId)).thenReturn(order.withStatus(OrderStatus.SUBMITTED));
    when(orderService.getOrder(orderId)).thenReturn(order);

    assertThat(controller.create(request).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(controller.submit(orderId).getBody().status()).isEqualTo(OrderStatus.SUBMITTED);
    assertThat(controller.get(orderId).getBody()).isEqualTo(order);
    assertThat(controller.healthCheck()).containsEntry("status", "ok");
  }
}
