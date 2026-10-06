package com.samba.chaos.demo.client;

import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/** Http Exchange. */
@HttpExchange("/v1")
public interface InventoryClient {

  /** Get Exchange. */
  @GetExchange("/inventory/{sku}")
  Map<String, Object> getStock(@PathVariable("sku") String sku);

  /** Post Exchange. */
  @PostExchange("/inventory/reserve")
  Map<String, Object> reserve(@RequestBody ReserveRequest request);

  /** Reserve Request. */
  record ReserveRequest(String sku, int quantity) {}
}
