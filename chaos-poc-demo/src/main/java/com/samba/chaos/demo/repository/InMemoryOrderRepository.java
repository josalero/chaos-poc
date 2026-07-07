package com.samba.chaos.demo.repository;

import com.samba.chaos.demo.model.Order;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryOrderRepository implements OrderRepository {

  private final Map<UUID, Order> orders = new ConcurrentHashMap<>();
  private final Map<String, UUID> skuIndex = new ConcurrentHashMap<>();

  @Override
  public Order save(Order order) {
    orders.put(order.id(), order);
    skuIndex.put(order.sku().toUpperCase(), order.id());
    return order;
  }

  @Override
  public Optional<Order> findById(UUID id) {
    return Optional.ofNullable(orders.get(id));
  }

  @Override
  public boolean existsBySku(String sku) {
    return skuIndex.containsKey(sku.toUpperCase());
  }

  @Override
  public void clearAll() {
    orders.clear();
    skuIndex.clear();
  }
}
