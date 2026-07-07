package com.samba.chaos.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.samba.chaos.listener.message.ChaosAssaultConfig;
import com.samba.chaos.listener.message.ChaosCommandAction;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class ChaosActuatorClientTest {

  @Mock private RestTemplate restTemplate;

  private ChaosActuatorClient client;

  @BeforeEach
  void setUp() {
    ChaosListenerProperties properties = new ChaosListenerProperties();
    properties.setActuatorBaseUrl("http://localhost:8080/actuator/chaosmonkey");
    RetryTemplate noRetry = RetryTemplate.builder().maxAttempts(1).build();
    client = new ChaosActuatorClient(properties, restTemplate, noRetry);
    when(restTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(Void.class)))
        .thenReturn(ResponseEntity.ok().build());
  }

  @Test
  void configureAssaults_shouldSendCompleteConfigurationSnapshot() {
    ChaosAssaultConfig assault =
        new ChaosAssaultConfig(
            1,
            true,
            true,
            false,
            null,
            null,
            List.of("com.samba.chaos.demo.service.OrderService.placeOrder"),
            null);

    client.configureAssaults(assault);

    ArgumentCaptor<HttpEntity<?>> captor = ArgumentCaptor.forClass(HttpEntity.class);
    verify(restTemplate)
        .postForEntity(
            eq("http://localhost:8080/actuator/chaosmonkey/assaults"),
            captor.capture(),
            eq(Void.class));

    @SuppressWarnings("unchecked")
    Map<String, Object> body = (Map<String, Object>) captor.getValue().getBody();
    assertEquals(1, body.get("level"));
    assertEquals(true, body.get("deterministic"));
    assertEquals(false, body.get("latencyActive"));
    assertEquals(1000, body.get("latencyRangeStart"));
    assertEquals(3000, body.get("latencyRangeEnd"));
    assertEquals(true, body.get("exceptionsActive"));
    assertEquals(
        List.of("com.samba.chaos.demo.service.OrderService.placeOrder"),
        body.get("watchedCustomServices"));

    @SuppressWarnings("unchecked")
    Map<String, Object> exception = (Map<String, Object>) body.get("exception");
    assertEquals("java.lang.RuntimeException", exception.get("type"));
  }

  @Test
  void configure_shouldResetAssaultsBeforeConfigure() {
    ChaosAssaultConfig assault =
        new ChaosAssaultConfig(
            1, true, false, true, 250, 250,
            List.of("com.samba.chaos.demo.web.OrderController.create"),
            null);

    ChaosActuatorClient.ApplyResult result = client.apply(ChaosCommandAction.CONFIGURE, assault);

    assertTrue(result.succeeded());
    ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
    verify(restTemplate, times(2))
        .postForEntity(urlCaptor.capture(), any(HttpEntity.class), eq(Void.class));
    assertEquals(
        List.of(
            "http://localhost:8080/actuator/chaosmonkey/assaults",
            "http://localhost:8080/actuator/chaosmonkey/assaults"),
        urlCaptor.getAllValues());
  }

  @Test
  void configureAndEnable_shouldResetAssaultsBeforeConfigure() {
    ChaosAssaultConfig assault =
        new ChaosAssaultConfig(
            1, true, true, false, null, null,
            List.of("com.samba.chaos.demo.service.OrderService.placeOrder"),
            null);

    ChaosActuatorClient.ApplyResult result =
        client.apply(ChaosCommandAction.CONFIGURE_AND_ENABLE, assault);

    assertTrue(result.succeeded());
    verify(restTemplate, times(3))
        .postForEntity(any(String.class), any(HttpEntity.class), eq(Void.class));
  }

  @Test
  void applyDisable_shouldResetAssaultsBeforeDisable() {
    ChaosActuatorClient.ApplyResult result = client.apply(ChaosCommandAction.DISABLE, null);

    assertTrue(result.succeeded());
    verify(restTemplate, times(2))
        .postForEntity(any(String.class), any(HttpEntity.class), eq(Void.class));
  }

  @Test
  void resetAssaults_shouldRestoreCanonicalDefaults() {
    client.resetAssaults();

    ArgumentCaptor<HttpEntity<?>> captor = ArgumentCaptor.forClass(HttpEntity.class);
    verify(restTemplate)
        .postForEntity(
            eq("http://localhost:8080/actuator/chaosmonkey/assaults"),
            captor.capture(),
            eq(Void.class));

    @SuppressWarnings("unchecked")
    Map<String, Object> body = (Map<String, Object>) captor.getValue().getBody();
    assertEquals(1, body.get("level"));
    assertEquals(true, body.get("deterministic"));
    assertEquals(false, body.get("latencyActive"));
    assertEquals(1000, body.get("latencyRangeStart"));
    assertEquals(3000, body.get("latencyRangeEnd"));
    assertEquals(false, body.get("exceptionsActive"));
    assertEquals(List.of(), body.get("watchedCustomServices"));

    @SuppressWarnings("unchecked")
    Map<String, Object> exception = (Map<String, Object>) body.get("exception");
    assertEquals("java.lang.RuntimeException", exception.get("type"));
  }
}
