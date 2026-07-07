package com.samba.chaos.demo.repository;

import com.samba.chaos.demo.model.Order;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {

  Order save(Order order);

  Optional<Order> findById(UUID id);

  boolean existsBySku(String sku);

  void clearAll();
}
