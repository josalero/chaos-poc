package com.samba.chaos.demo.client;

import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "inventory", url = "${samba.chaos.demo.inventory.base-url}")
public interface InventoryClient {

  @GetMapping("/v1/inventory/{sku}")
  Map<String, Object> getStock(@PathVariable("sku") String sku);

  @PostMapping("/v1/inventory/reserve")
  Map<String, Object> reserve(@RequestBody ReserveRequest request);

  record ReserveRequest(String sku, int quantity) {}
}
