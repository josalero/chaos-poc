package com.samba.chaos.demo.client;

import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "authorization", url = "${samba.chaos.demo.inventory.base-url}")
public interface AuthorizationClient {

  @GetMapping("/v1/auth/{resource}")
  Map<String, String> checkAccess(@PathVariable("resource") String resource);
}
