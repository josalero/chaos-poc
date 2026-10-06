package com.samba.chaos.demo.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.samba.chaos.demo.model.Order;
import com.samba.chaos.demo.model.OrderStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryOrderRepositoryTest {

  @Test
  void findsSavedOrdersById() {
    InMemoryOrderRepository repository = new InMemoryOrderRepository();
    Order order = new Order(UUID.randomUUID(), "widget-1", 1, OrderStatus.CREATED, Instant.now());

    repository.save(order);

    assertThat(repository.findById(order.id())).contains(order);
    assertThat(repository.existsBySku("WIDGET-1")).isTrue();
  }
}
