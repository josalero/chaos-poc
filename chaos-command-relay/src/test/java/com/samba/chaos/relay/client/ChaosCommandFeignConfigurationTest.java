package com.samba.chaos.relay.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import feign.RequestTemplate;
import java.lang.annotation.Annotation;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Component;

class ChaosCommandFeignConfigurationTest {

  @Test
  void staysOutOfTheApplicationComponentScan() {
    assertThat(ChaosCommandFeignConfiguration.class.getAnnotations())
        .extracting(Annotation::annotationType)
        .doesNotContain(Configuration.class, Component.class);
  }

  @Test
  void rejectsWhenTheAccessTokenIsMissing() {
    OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);
    when(manager.authorize(any())).thenReturn(null);

    assertThatThrownBy(
            () ->
                new ChaosCommandFeignConfiguration()
                    .chaosCommandBearerToken(manager)
                    .apply(new RequestTemplate()))
        .isInstanceOf(ClientAuthorizationException.class);
  }
}
