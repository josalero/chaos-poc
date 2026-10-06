package com.samba.chaos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.samba.chaos.command.ChaosAssaultConfig;
import com.samba.chaos.command.ChaosCommandAction;
import com.samba.chaos.config.ChaosProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.backoff.FixedBackOff;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ChaosActuatorClientTest {

  private static final String BASE = "http://localhost:8080/actuator/chaosmonkey";

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private MockRestServiceServer server;
  private ChaosActuatorClient client;
  private ChaosProperties properties;

  @BeforeEach
  void setUp() {
    properties = new ChaosProperties();
    properties.setActuatorBaseUrl(BASE);
    properties.setMaxApplyAttempts(1);
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    client = new ChaosActuatorClient(properties, builder.build(), retryTemplate(0));
  }

  @Test
  void configureAssaults_shouldSendCompleteConfigurationSnapshot() throws Exception {
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
    server.expect(requestTo(BASE + "/assaults")).andRespond(withSuccess());

    client.configureAssaults(assault);

    server.verify();
  }

  @Test
  void configure_shouldResetAssaultsBeforeConfigure() {
    ChaosAssaultConfig assault =
        new ChaosAssaultConfig(
            1,
            true,
            false,
            true,
            250,
            250,
            List.of("com.samba.chaos.demo.web.OrderController.create"),
            null);
    server.expect(requestTo(BASE + "/assaults")).andRespond(withSuccess());
    server.expect(requestTo(BASE + "/assaults")).andRespond(withSuccess());

    ChaosActuatorClient.ApplyResult result = client.apply(ChaosCommandAction.CONFIGURE, assault);

    assertTrue(result.succeeded());
    server.verify();
  }

  @Test
  void configureAndEnable_shouldResetThenConfigureThenEnable() {
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
    server.expect(requestTo(BASE + "/assaults")).andRespond(withSuccess());
    server.expect(requestTo(BASE + "/assaults")).andRespond(withSuccess());
    server.expect(requestTo(BASE + "/enable")).andRespond(withSuccess());

    ChaosActuatorClient.ApplyResult result =
        client.apply(ChaosCommandAction.CONFIGURE_AND_ENABLE, assault);

    assertTrue(result.succeeded());
    server.verify();
  }

  @Test
  void applyDisable_shouldResetAssaultsBeforeDisable() {
    server.expect(requestTo(BASE + "/assaults")).andRespond(withSuccess());
    server.expect(requestTo(BASE + "/disable")).andRespond(withSuccess());

    ChaosActuatorClient.ApplyResult result = client.apply(ChaosCommandAction.DISABLE, null);

    assertTrue(result.succeeded());
    server.verify();
  }

  @Test
  void resetAssaults_shouldRestoreCanonicalDefaults() throws Exception {
    server
        .expect(requestTo(BASE + "/assaults"))
        .andExpect(
            request -> {
              String json = ((MockClientHttpRequest) request).getBodyAsString();
              JsonNode body = JSON.readTree(json);
              assertEquals(1, body.path("level").asInt());
              assertEquals(true, body.path("deterministic").asBoolean());
              assertEquals(false, body.path("latencyActive").asBoolean());
              assertEquals(1000, body.path("latencyRangeStart").asInt());
              assertEquals(3000, body.path("latencyRangeEnd").asInt());
              assertEquals(false, body.path("exceptionsActive").asBoolean());
              assertEquals(0, body.path("watchedCustomServices").size());
              assertEquals(
                  "java.lang.RuntimeException", body.path("exception").path("type").asString());
            })
        .andRespond(withSuccess());

    client.resetAssaults();

    server.verify();
  }

  @Test
  void transientActuatorFailureIsRetriedUntilItSucceeds() {
    properties.setMaxApplyAttempts(3);
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer retrying = MockRestServiceServer.bindTo(builder).build();
    ChaosActuatorClient retryingClient =
        new ChaosActuatorClient(properties, builder.build(), retryTemplate(2));
    retrying
        .expect(requestTo(BASE + "/enable"))
        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
    retrying.expect(requestTo(BASE + "/enable")).andRespond(withSuccess());

    ChaosActuatorClient.ApplyResult result = retryingClient.apply(ChaosCommandAction.ENABLE, null);

    assertTrue(result.succeeded());
    retrying.verify();
  }

  @Test
  void permanentActuatorFailureIsNotRetried() {
    properties.setMaxApplyAttempts(3);
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer once = MockRestServiceServer.bindTo(builder).build();
    ChaosActuatorClient retryingClient =
        new ChaosActuatorClient(properties, builder.build(), retryTemplate(2));
    once.expect(requestTo(BASE + "/enable"))
        .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON));

    ChaosActuatorClient.ApplyResult result = retryingClient.apply(ChaosCommandAction.ENABLE, null);

    assertFalse(result.succeeded());
    assertEquals(400, result.httpStatus());
    once.verify();
  }

  @Test
  void unreachableActuatorIsRetriedThenReportedAsFailure() {
    properties.setMaxApplyAttempts(2);
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer down = MockRestServiceServer.bindTo(builder).build();
    ChaosActuatorClient retryingClient =
        new ChaosActuatorClient(properties, builder.build(), retryTemplate(1));
    down.expect(requestTo(BASE + "/enable"))
        .andRespond(
            request -> {
              throw new ResourceAccessException("connection refused");
            });
    down.expect(requestTo(BASE + "/enable"))
        .andRespond(
            request -> {
              throw new ResourceAccessException("connection refused");
            });

    ChaosActuatorClient.ApplyResult result = retryingClient.apply(ChaosCommandAction.ENABLE, null);

    assertFalse(result.succeeded());
    down.verify();
  }

  @Test
  void configureStopsWhenResetFails() {
    server.expect(requestTo(BASE + "/assaults")).andRespond(withStatus(HttpStatus.BAD_REQUEST));

    ChaosActuatorClient.ApplyResult result = client.apply(ChaosCommandAction.CONFIGURE, null);

    assertFalse(result.succeeded());
    assertEquals("assaults", result.failedStep());
  }

  @Test
  void disableStopsWhenResetFails() {
    server.expect(requestTo(BASE + "/assaults")).andRespond(withStatus(HttpStatus.BAD_REQUEST));

    ChaosActuatorClient.ApplyResult result = client.apply(ChaosCommandAction.DISABLE, null);

    assertFalse(result.succeeded());
    assertEquals("assaults", result.failedStep());
  }

  @Test
  void disableStopsWhenDisableCallFails() {
    server.expect(requestTo(BASE + "/assaults")).andRespond(withSuccess());
    server.expect(requestTo(BASE + "/disable")).andRespond(withStatus(HttpStatus.BAD_REQUEST));

    ChaosActuatorClient.ApplyResult result = client.apply(ChaosCommandAction.DISABLE, null);

    assertFalse(result.succeeded());
    assertEquals("disable", result.failedStep());
  }

  @Test
  void configureAndEnableStopsWhenResetFails() {
    server.expect(requestTo(BASE + "/assaults")).andRespond(withStatus(HttpStatus.BAD_REQUEST));

    ChaosActuatorClient.ApplyResult result =
        client.apply(ChaosCommandAction.CONFIGURE_AND_ENABLE, null);

    assertFalse(result.succeeded());
    assertEquals("assaults", result.failedStep());
  }

  @Test
  void configureAndEnableStopsWhenConfigureFails() {
    ChaosAssaultConfig assault = minimalAssault();
    server.expect(requestTo(BASE + "/assaults")).andRespond(withSuccess());
    server.expect(requestTo(BASE + "/assaults")).andRespond(withStatus(HttpStatus.BAD_REQUEST));

    ChaosActuatorClient.ApplyResult result =
        client.apply(ChaosCommandAction.CONFIGURE_AND_ENABLE, assault);

    assertFalse(result.succeeded());
    assertEquals("assaults", result.failedStep());
  }

  @Test
  void configureAndEnableStopsWhenEnableFails() {
    ChaosAssaultConfig assault = minimalAssault();
    server.expect(requestTo(BASE + "/assaults")).andRespond(withSuccess());
    server.expect(requestTo(BASE + "/assaults")).andRespond(withSuccess());
    server.expect(requestTo(BASE + "/enable")).andRespond(withStatus(HttpStatus.BAD_REQUEST));

    ChaosActuatorClient.ApplyResult result =
        client.apply(ChaosCommandAction.CONFIGURE_AND_ENABLE, assault);

    assertFalse(result.succeeded());
    assertEquals("enable", result.failedStep());
  }

  @Test
  void configureAssaultsPostsExplicitExceptionNode() throws Exception {
    JsonNode exception = JSON.readTree("{\"type\":\"java.lang.IllegalStateException\"}");
    ChaosAssaultConfig assault =
        new ChaosAssaultConfig(null, null, null, null, null, null, null, exception);
    server
        .expect(requestTo(BASE + "/assaults"))
        .andExpect(
            request -> {
              JsonNode body = JSON.readTree(((MockClientHttpRequest) request).getBodyAsString());
              assertEquals(
                  "java.lang.IllegalStateException",
                  body.path("exception").path("type").asString());
            })
        .andRespond(withSuccess());

    client.configureAssaults(assault);

    server.verify();
  }

  @Test
  void unexpectedRetryCauseReportsStatusZero() throws Exception {
    RetryTemplate template = mock(RetryTemplate.class);
    when(template.execute(any()))
        .thenThrow(new RetryException("exhausted", new IllegalStateException("boom")));
    ChaosActuatorClient failing =
        new ChaosActuatorClient(properties, RestClient.create(), template);

    ChaosActuatorClient.ApplyResult result = failing.apply(ChaosCommandAction.ENABLE, null);

    assertFalse(result.succeeded());
    assertEquals(0, result.httpStatus());
  }

  private static ChaosAssaultConfig minimalAssault() {
    return new ChaosAssaultConfig(1, true, false, false, 250, 250, List.of(), null);
  }

  private static RetryTemplate retryTemplate(long maxRetries) {
    RetryPolicy policy =
        RetryPolicy.builder()
            .backOff(new FixedBackOff(1, maxRetries))
            .includes(
                com.samba.chaos.exception.TransientActuatorException.class,
                ResourceAccessException.class)
            .build();
    return new RetryTemplate(policy);
  }
}
