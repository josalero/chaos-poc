package com.samba.chaos.relay.client;

import static com.samba.chaos.relay.config.HttpClientConfig.CHAOS_CLIENT_REGISTRATION;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.OAuth2Error;

/**
 * Child-context settings for {@link ChaosCommandClient}.
 *
 * <p>This type has no Spring stereotype. {@code @EnableFeignClients} registers it only in the Feign
 * client context. A stereotype on this class would publish the beans into the relay application
 * context.
 */
public class ChaosCommandFeignConfiguration {

  /**
   * Dispatch threads have no operator security context, so the token is requested for a fixed relay
   * principal.
   */
  private static final Authentication RELAY_PRINCIPAL =
      new AnonymousAuthenticationToken(
          CHAOS_CLIENT_REGISTRATION,
          CHAOS_CLIENT_REGISTRATION,
          AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

  /**
   * Adds the client-credentials bearer token to each command call.
   *
   * @param authorizedClientManager token client
   * @return the Feign interceptor
   */
  @Bean
  public RequestInterceptor chaosCommandBearerToken(
      OAuth2AuthorizedClientManager authorizedClientManager) {
    return template -> {
      OAuth2AuthorizedClient client =
          authorizedClientManager.authorize(
              OAuth2AuthorizeRequest.withClientRegistrationId(CHAOS_CLIENT_REGISTRATION)
                  .principal(RELAY_PRINCIPAL)
                  .build());
      if (client == null || client.getAccessToken() == null) {
        throw new ClientAuthorizationException(
            new OAuth2Error("authorization_failed"), CHAOS_CLIENT_REGISTRATION);
      }
      template.header(
          HttpHeaders.AUTHORIZATION, "Bearer " + client.getAccessToken().getTokenValue());
    };
  }

  /**
   * Drops the cached token when an instance rejects it, so the next command requests a new one.
   *
   * @param authorizedClients token store
   * @return the Feign error decoder
   */
  @Bean
  public ErrorDecoder chaosCommandErrorDecoder(OAuth2AuthorizedClientService authorizedClients) {
    ErrorDecoder fallback = new ErrorDecoder.Default();
    return (methodKey, response) -> {
      if (response.status() == 401 || response.status() == 403) {
        authorizedClients.removeAuthorizedClient(
            CHAOS_CLIENT_REGISTRATION, CHAOS_CLIENT_REGISTRATION);
      }
      return fallback.decode(methodKey, response);
    };
  }
}
