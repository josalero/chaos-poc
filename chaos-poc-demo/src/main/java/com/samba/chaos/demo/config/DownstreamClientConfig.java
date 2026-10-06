package com.samba.chaos.demo.config;

import com.samba.chaos.demo.client.AuthorizationClient;
import com.samba.chaos.demo.client.InventoryClient;
import java.net.URI;
import java.util.Map;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import org.springframework.web.util.UriComponentsBuilder;

@Configuration
class DownstreamClientConfig {

  @Bean
  HttpServiceProxyFactory downstreamProxyFactory(LoadBalancerClient loadBalancerClient) {
    RestClient client =
        RestClient.builder()
            .baseUrl("http://chaos-poc-downstream")
            .requestInterceptor(
                (request, body, execution) -> {
                  ServiceInstance instance = loadBalancerClient.choose(request.getURI().getHost());
                  if (instance == null) {
                    throw new IllegalStateException(
                        "No UP instance of " + request.getURI().getHost());
                  }
                  URI target =
                      UriComponentsBuilder.fromUri(request.getURI())
                          .scheme(instance.isSecure() ? "https" : "http")
                          .host(instance.getHost())
                          .port(instance.getPort())
                          .build(true)
                          .toUri();
                  return execution.execute(new RewrittenRequest(request, target), body);
                })
            .build();
    return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(client)).build();
  }

  @Bean
  InventoryClient inventoryClient(HttpServiceProxyFactory downstreamProxyFactory) {
    return downstreamProxyFactory.createClient(InventoryClient.class);
  }

  @Bean
  AuthorizationClient authorizationClient(HttpServiceProxyFactory downstreamProxyFactory) {
    return downstreamProxyFactory.createClient(AuthorizationClient.class);
  }

  private record RewrittenRequest(HttpRequest delegate, URI uri) implements HttpRequest {

    @Override
    public HttpMethod getMethod() {
      return delegate.getMethod();
    }

    @Override
    public URI getURI() {
      return uri;
    }

    @Override
    public HttpHeaders getHeaders() {
      return delegate.getHeaders();
    }

    @Override
    public Map<String, Object> getAttributes() {
      return delegate.getAttributes();
    }
  }
}
