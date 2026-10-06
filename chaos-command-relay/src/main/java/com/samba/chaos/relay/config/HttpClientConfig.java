package com.samba.chaos.relay.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.client.RestClient;

/**
 * HTTP clients used to reach chaos-lib and the demo admin reset.
 *
 * <p>{@code chaosDispatchExecutor} runs fan-out on virtual threads. Set {@code
 * chaos.relay.dispatch-synchronous=true} to run each post on the caller thread.
 */
@Configuration
public class HttpClientConfig {

  /** OAuth2 client registration id used to mint the chaos-lib bearer token. */
  public static final String CHAOS_CLIENT_REGISTRATION = "chaos-command-relay";

  @Bean
  OAuth2AuthorizedClientManager chaosAuthorizedClientManager(
      ClientRegistrationRepository clientRegistrations,
      OAuth2AuthorizedClientService authorizedClients) {
    AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
        new AuthorizedClientServiceOAuth2AuthorizedClientManager(
            clientRegistrations, authorizedClients);
    manager.setAuthorizedClientProvider(
        OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());
    return manager;
  }

  @Bean
  RestClient instanceRestClient(RestClient.Builder builder) {
    return builder.build();
  }

  @Bean(name = "chaosDispatchExecutor")
  TaskExecutor chaosDispatchExecutor(
      @Value("${chaos.relay.dispatch-synchronous:false}") boolean synchronous) {
    if (synchronous) {
      return Runnable::run;
    }
    SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("chaos-dispatch-");
    executor.setVirtualThreads(true);
    executor.setConcurrencyLimit(32);
    return executor;
  }
}
