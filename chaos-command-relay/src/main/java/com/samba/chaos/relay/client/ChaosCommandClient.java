package com.samba.chaos.relay.client;

import com.samba.chaos.command.ChaosCommandMessage;
import com.samba.chaos.command.ChaosCommandResult;
import com.samba.chaos.web.ChaosCommandEndpoint;
import java.net.URI;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client for the chaos-lib command endpoint. Callers pass the discovered instance URI so one
 * client posts to every pod. A fixed service name would let the load balancer choose a single
 * instance.
 */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
@FeignClient(
    name = "chaos-command",
    contextId = "chaos-command",
    url = "${chaos.relay.command-client-url:http://localhost}",
    configuration = ChaosCommandFeignConfiguration.class)
public interface ChaosCommandClient {

  /**
   * Posts the command to one instance.
   *
   * @param instance base URI of that instance, for example {@code http://10.0.0.8:8080}
   * @param message command body
   * @return the instance result
   */
  @PostMapping(
      path = ChaosCommandEndpoint.PATH,
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  ChaosCommandResult apply(URI instance, @RequestBody ChaosCommandMessage message);
}
