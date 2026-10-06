package com.samba.chaos.relay.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

class HttpClientConfigTest {

  @Test
  void buildsClientCredentialsManagerAndVirtualThreadExecutor() {
    HttpClientConfig config = new HttpClientConfig();

    OAuth2AuthorizedClientManager manager =
        config.chaosAuthorizedClientManager(
            mock(ClientRegistrationRepository.class), mock(OAuth2AuthorizedClientService.class));
    TaskExecutor executor = config.chaosDispatchExecutor(false);

    assertThat(manager).isNotNull();
    assertThat(executor).isInstanceOf(SimpleAsyncTaskExecutor.class);
  }
}
