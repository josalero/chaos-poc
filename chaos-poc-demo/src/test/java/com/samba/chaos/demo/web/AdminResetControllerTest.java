package com.samba.chaos.demo.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.samba.chaos.demo.model.Order;
import com.samba.chaos.demo.model.OrderStatus;
import com.samba.chaos.demo.repository.OrderRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AdminResetControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private OrderRepository orderRepository;

  @Test
  void reset_shouldClearInMemoryOrders() throws Exception {
    orderRepository.save(
        new Order(UUID.randomUUID(), "WIDGET-1", 1, OrderStatus.CREATED, Instant.now()));

    mockMvc
        .perform(post("/api/v1/admin/reset"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("OK"));

    org.junit.jupiter.api.Assertions.assertFalse(orderRepository.existsBySku("WIDGET-1"));
  }
}
