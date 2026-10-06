package com.samba.chaos.relay.console;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.samba.chaos.relay.service.TargetInstancesResolver;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ChaosDemoAdminClientTest {

  @Test
  void returnsFalseWhenNoInstancesAreUp() {
    TargetInstancesResolver resolver = mock(TargetInstancesResolver.class);
    when(resolver.resolveUp("orders")).thenReturn(List.of());

    boolean cleared =
        new ChaosDemoAdminClient(resolver, RestClient.create()).resetDemoData("orders");

    assertThat(cleared).isFalse();
  }

  @Test
  void postsResetToEveryUpInstance() {
    TargetInstancesResolver resolver = mock(TargetInstancesResolver.class);
    when(resolver.resolveUp("orders"))
        .thenReturn(List.of(new DefaultServiceInstance("pod-a", "orders", "demo", 8080, false)));
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server.expect(requestTo("http://demo:8080/api/v1/admin/reset")).andRespond(withSuccess());

    boolean cleared = new ChaosDemoAdminClient(resolver, builder.build()).resetDemoData("orders");

    assertThat(cleared).isTrue();
    server.verify();
  }

  @Test
  void wrapsResetFailure() {
    TargetInstancesResolver resolver = mock(TargetInstancesResolver.class);
    when(resolver.resolveUp("orders"))
        .thenReturn(List.of(new DefaultServiceInstance("pod-a", "orders", "demo", 8080, false)));
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("http://demo:8080/api/v1/admin/reset"))
        .andRespond(
            org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(
                HttpStatus.INTERNAL_SERVER_ERROR));

    assertThatThrownBy(
            () -> new ChaosDemoAdminClient(resolver, builder.build()).resetDemoData("orders"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("orders");
  }
}
