package com.samba.chaos.demo.client;

import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/** Http Exchange. */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
@HttpExchange("/v1")
public interface AuthorizationClient {

  /** Get Exchange. */
  @GetExchange("/auth/{resource}")
  Map<String, String> checkAccess(@PathVariable("resource") String resource);
}
