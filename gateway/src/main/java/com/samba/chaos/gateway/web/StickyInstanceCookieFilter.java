package com.samba.chaos.gateway.web;

import java.util.Objects;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerProperties;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.ReactiveLoadBalancerClientFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
class StickyInstanceCookieFilter implements GlobalFilter, Ordered {

  private final String cookieName;

  StickyInstanceCookieFilter(LoadBalancerProperties properties) {
    this.cookieName = properties.getStickySession().getInstanceIdCookieName();
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    Response<ServiceInstance> chosen =
        exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_LOADBALANCER_RESPONSE_ATTR);
    if (chosen != null && chosen.hasServer()) {
      exchange
          .getResponse()
          .addCookie(
              ResponseCookie.from(
                      cookieName, Objects.requireNonNull(chosen.getServer()).getInstanceId())
                  .path("/")
                  .httpOnly(true)
                  .sameSite("Lax")
                  .build());
    }
    return chain.filter(exchange);
  }

  @Override
  public int getOrder() {
    return ReactiveLoadBalancerClientFilter.LOAD_BALANCER_CLIENT_FILTER_ORDER + 1;
  }
}
