package com.samba.chaos.gateway.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.loadbalancer.DefaultResponse;
import org.springframework.cloud.client.loadbalancer.LoadBalancerProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class StickyInstanceCookieFilterTest {

  @Test
  void writesTheChosenInstanceIdCookie() {
    LoadBalancerProperties properties = new LoadBalancerProperties();
    properties.getStickySession().setInstanceIdCookieName("sc-lb-instance-id");
    StickyInstanceCookieFilter filter = new StickyInstanceCookieFilter(properties);
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/demo/orders").build());
    exchange
        .getAttributes()
        .put(
            ServerWebExchangeUtils.GATEWAY_LOADBALANCER_RESPONSE_ATTR,
            new DefaultResponse(
                new DefaultServiceInstance("pod-a", "chaos-poc-demo", "10.0.0.8", 8080, false)));
    GatewayFilterChain chain = mock(GatewayFilterChain.class);
    when(chain.filter(exchange)).thenReturn(Mono.empty());

    filter.filter(exchange, chain).block();

    ResponseCookie cookie = exchange.getResponse().getCookies().getFirst("sc-lb-instance-id");
    assertThat(cookie).isNotNull();
    assertThat(cookie.getValue()).isEqualTo("pod-a");
    assertThat(cookie.isHttpOnly()).isTrue();
  }
}
