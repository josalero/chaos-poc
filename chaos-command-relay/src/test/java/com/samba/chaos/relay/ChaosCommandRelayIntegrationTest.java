package com.samba.chaos.relay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.samba.chaos.listener.message.ChaosCommandAction;
import com.samba.chaos.relay.model.ChaosCommandStatusResponse;
import com.samba.chaos.relay.model.ChaosCommandSubmitResponse;
import com.samba.chaos.relay.model.CommandAggregateStatus;
import com.samba.chaos.listener.ChaosListenerAutoConfiguration;
import com.samba.chaos.relay.testsupport.FakeChaosMonkeyController;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
    classes = {ChaosCommandRelayApplication.class, FakeChaosMonkeyController.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({ChaosListenerAutoConfiguration.class, ChaosListenerTestConfiguration.class})
@Testcontainers(disabledWithoutDocker = true)
class ChaosCommandRelayIntegrationTest {

  @Container static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management");

  @DynamicPropertySource
  static void rabbitProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.rabbitmq.host", rabbit::getHost);
    registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
    registry.add("chaos.relay.expected-instances-fallback.chaos-poc-demo", () -> "1");
    registry.add("chaos.command-listener.enabled", () -> "true");
  }

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void shouldPublishCommandAndReachAppliedStatus() {
    Map<String, Object> body =
        Map.of(
            "environment",
            "test",
            "targetApplication",
            "chaos-poc-demo",
            "action",
            "CONFIGURE_AND_ENABLE",
            "issuedBy",
            "integration-test",
            "correlationId",
            "DSU-1671-poc",
            "expiresAt",
            Instant.now().plusSeconds(3600).toString(),
            "assault",
            Map.of(
                "level",
                1,
                "exceptionsActive",
                true,
                "watchedCustomServices",
                List.of("OrderService.placeOrder")));

    ResponseEntity<ChaosCommandSubmitResponse> submit =
        restTemplate.postForEntity("/internal/v1/chaos/commands", body, ChaosCommandSubmitResponse.class);

    assertThat(submit.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(submit.getBody()).isNotNull();
    assertThat(submit.getBody().expectedInstances()).isEqualTo(1);

    String statusUrl = submit.getBody().statusUrl();

    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () -> {
              ResponseEntity<ChaosCommandStatusResponse> status =
                  restTemplate.getForEntity(statusUrl, ChaosCommandStatusResponse.class);
              assertThat(status.getBody()).isNotNull();
              assertThat(status.getBody().status()).isEqualTo(CommandAggregateStatus.APPLIED);
              assertThat(status.getBody().successCount()).isEqualTo(1);
            });
  }

  @Test
  void shouldRejectInvalidTargetApplication() {
    Map<String, Object> body =
        Map.of(
            "environment",
            "test",
            "targetApplication",
            "unknown-service",
            "action",
            "DISABLE",
            "issuedBy",
            "integration-test");

    ResponseEntity<Map> response =
        restTemplate.postForEntity("/internal/v1/chaos/commands", body, Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).containsEntry("status", "REJECTED");
  }
}
