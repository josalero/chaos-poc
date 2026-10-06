package com.samba.chaos.demo.repository;

import com.samba.chaos.demo.model.Order;
import java.util.Optional;
import java.util.UUID;

/** Order Repository. */
public interface OrderRepository {

  /** Save. */
  Order save(Order order);

  /** Find By Id. */
  Optional<Order> findById(UUID id);

  /** Exists By Sku. */
  boolean existsBySku(String sku);

  /** Clear All. */
  void clearAll();
}
