package com.samba.chaos.relay.console;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.samba.chaos.relay.config.JacksonConfiguration;
import com.samba.chaos.relay.service.TargetInstancesResolver;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ChaosMonkeyActuatorProbeTest {

  @Test
  void probe_returnsUnconfiguredWhenNoInstancesAreUp() {
    TargetInstancesResolver resolver = mock(TargetInstancesResolver.class);
    when(resolver.resolveUp("chaos-poc-demo")).thenReturn(List.of());
    ChaosMonkeyActuatorProbe probe =
        new ChaosMonkeyActuatorProbe(
            resolver, RestClient.create(), new JacksonConfiguration().objectMapper());

    ChaosMonkeyRuntimeSnapshot snapshot = probe.probe("chaos-poc-demo");

    assertThat(snapshot.configured()).isFalse();
    assertThat(snapshot.reachable()).isFalse();
  }

  @Test
  void probe_returnsLiveStatusAndAssaults() {
    TargetInstancesResolver resolver = mock(TargetInstancesResolver.class);
    when(resolver.resolveUp("chaos-poc-demo"))
        .thenReturn(
            List.of(new DefaultServiceInstance("pod-a", "chaos-poc-demo", "demo", 8080, false)));
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("http://demo:8080/actuator/chaosmonkey/status"))
        .andRespond(withSuccess("{\"enabled\":true}", MediaType.APPLICATION_JSON));
    server
        .expect(requestTo("http://demo:8080/actuator/chaosmonkey/assaults"))
        .andRespond(
            withSuccess("{\"latencyActive\":true,\"level\":1}", MediaType.APPLICATION_JSON));
    ChaosMonkeyActuatorProbe probe =
        new ChaosMonkeyActuatorProbe(
            resolver, builder.build(), new JacksonConfiguration().objectMapper());

    ChaosMonkeyRuntimeSnapshot snapshot = probe.probe("chaos-poc-demo");

    assertThat(snapshot.reachable()).isTrue();
    assertThat(snapshot.enabled()).isTrue();
    assertThat(snapshot.statusJson()).contains("enabled");
    assertThat(snapshot.assaultsJson()).contains("latencyActive");
    server.verify();
  }

  @Test
  void probe_returnsUnreachableWhenEveryInstanceFails() {
    TargetInstancesResolver resolver = mock(TargetInstancesResolver.class);
    when(resolver.resolveUp("orders"))
        .thenReturn(List.of(new DefaultServiceInstance("pod-a", "orders", "demo", 8080, false)));
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("http://demo:8080/actuator/chaosmonkey/status"))
        .andRespond(
            request -> {
              throw new org.springframework.web.client.ResourceAccessException("down");
            });
    ChaosMonkeyActuatorProbe probe =
        new ChaosMonkeyActuatorProbe(
            resolver, builder.build(), new JacksonConfiguration().objectMapper());

    ChaosMonkeyRuntimeSnapshot snapshot = probe.probe("orders");

    assertThat(snapshot.reachable()).isFalse();
    assertThat(snapshot.errorMessage()).contains("down");
  }

  @Test
  void probe_ignoresEmptyAssaultDocument() {
    TargetInstancesResolver resolver = mock(TargetInstancesResolver.class);
    when(resolver.resolveUp("orders"))
        .thenReturn(List.of(new DefaultServiceInstance("pod-a", "orders", "demo", 8080, false)));
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("http://demo:8080/actuator/chaosmonkey/status"))
        .andRespond(withSuccess("null", MediaType.APPLICATION_JSON));
    server
        .expect(requestTo("http://demo:8080/actuator/chaosmonkey/assaults"))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
    ChaosMonkeyActuatorProbe probe =
        new ChaosMonkeyActuatorProbe(
            resolver, builder.build(), new JacksonConfiguration().objectMapper());

    ChaosMonkeyRuntimeSnapshot snapshot = probe.probe("orders");

    assertThat(snapshot.enabled()).isFalse();
    assertThat(snapshot.statusJson()).isNull();
    assertThat(snapshot.assaultsJson()).isNull();
  }
}
