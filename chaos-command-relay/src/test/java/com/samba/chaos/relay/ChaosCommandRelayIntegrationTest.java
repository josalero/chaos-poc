package com.samba.chaos.relay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.samba.chaos.command.ChaosCommandAction;
import com.samba.chaos.command.InstanceOutcome;
import com.samba.chaos.relay.config.HttpClientConfig;
import com.samba.chaos.relay.config.JacksonConfiguration;
import com.samba.chaos.relay.model.ChaosCommandRequest;
import com.samba.chaos.relay.model.ChaosCommandStatusResponse;
import com.samba.chaos.relay.model.ChaosInstanceStatus;
import com.samba.chaos.relay.model.CommandAggregateStatus;
import com.samba.chaos.relay.service.ChaosCommandService;
import feign.Client;
import feign.Request;
import feign.Response;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    properties = {
      "spring.cloud.config.enabled=false",
      "spring.cloud.config.import-check.enabled=false",
      "eureka.client.enabled=false",
      "samba.chaos.command.enabled=false",
      "chaos.relay.dispatch-synchronous=true",
      "spring.cloud.discovery.client.simple.instances.chaos-poc-demo[0].uri=http://pod-a:8080",
      "spring.cloud.discovery.client.simple.instances.chaos-poc-demo[0].instance-id=pod-a",
      "spring.cloud.discovery.client.simple.instances.chaos-poc-demo[1].uri=http://pod-b:8080",
      "spring.cloud.discovery.client.simple.instances.chaos-poc-demo[1].instance-id=pod-b"
    })
@Import(ChaosCommandRelayIntegrationTest.CommandFeignStub.class)
class ChaosCommandRelayIntegrationTest {

  private static final String ACCESS_TOKEN = "test-access-token";
  private static final String POD_A = "http://pod-a:8080/internal/chaos/commands";
  private static final String POD_B = "http://pod-b:8080/internal/chaos/commands";

  @Autowired private RecordingFeignClient pods;
  @Autowired private ChaosCommandService commandService;
  @Autowired private ClientRegistrationRepository clientRegistrations;
  @MockitoBean private OAuth2AuthorizedClientManager authorizedClientManager;

  @BeforeEach
  void issueAccessToken() {
    pods.reset();
    Instant issuedAt = Instant.now();
    OAuth2AccessToken token =
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER, ACCESS_TOKEN, issuedAt, issuedAt.plusSeconds(300));
    when(authorizedClientManager.authorize(any()))
        .thenReturn(
            new OAuth2AuthorizedClient(
                clientRegistrations.findByRegistrationId(
                    HttpClientConfig.CHAOS_CLIENT_REGISTRATION),
                HttpClientConfig.CHAOS_CLIENT_REGISTRATION,
                token));
  }

  @Test
  void submit_whenBothInstancesSucceed_reportsApplied() {
    pods.whenUrl(POD_A, request -> authorizedJson(request, "pod-a", "SUCCESS"));
    pods.whenUrl(
        POD_B, request -> json(request, 200, resultJson(commandId(request), "pod-b", "SUCCESS")));

    UUID id = submitAccepted();

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              ChaosCommandStatusResponse status = commandService.getStatus(id).orElseThrow();
              assertThat(status.expectedInstances()).isEqualTo(2);
              assertThat(status.status()).isEqualTo(CommandAggregateStatus.APPLIED);
              assertThat(status.successCount()).isEqualTo(2);
            });
  }

  @Test
  void submit_whenOneInstanceReturns500_reportsFailed() {
    pods.whenUrl(
        POD_A, request -> json(request, 200, resultJson(commandId(request), "pod-a", "SUCCESS")));
    pods.whenUrl(POD_B, request -> json(request, 500, ""));

    UUID id = submitAccepted();

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              ChaosCommandStatusResponse status = commandService.getStatus(id).orElseThrow();
              assertThat(status.status()).isEqualTo(CommandAggregateStatus.FAILED);
              assertThat(status.instances())
                  .extracting(ChaosInstanceStatus::outcome)
                  .contains(InstanceOutcome.REJECTED);
            });
  }

  @Test
  void submit_whenOneOfTwoInstancesIsUnreachable_reportsFailedWithUnreachableOutcome() {
    pods.whenUrl(POD_A, request -> authorizedJson(request, "pod-a", "SUCCESS"));
    pods.whenUrl(
        POD_B,
        request -> {
          throw new SocketTimeoutException("read timed out");
        });

    UUID id = submitAccepted();

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              ChaosCommandStatusResponse status = commandService.getStatus(id).orElseThrow();
              assertThat(status.expectedInstances()).isEqualTo(2);
              assertThat(status.status()).isEqualTo(CommandAggregateStatus.FAILED);
              assertThat(status.instances())
                  .extracting(ChaosInstanceStatus::outcome)
                  .containsExactlyInAnyOrder(InstanceOutcome.SUCCESS, InstanceOutcome.UNREACHABLE);
            });
  }

  @Test
  void submit_whenInstanceRejectsTheToken_reportsRejectedWithStatus() {
    pods.whenUrl(
        POD_A, request -> json(request, 200, resultJson(commandId(request), "pod-a", "SUCCESS")));
    pods.whenUrl(POD_B, request -> json(request, 403, ""));

    UUID id = submitAccepted();

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              ChaosCommandStatusResponse status = commandService.getStatus(id).orElseThrow();
              assertThat(status.status()).isEqualTo(CommandAggregateStatus.FAILED);
              assertThat(status.instances())
                  .filteredOn(instance -> instance.outcome() == InstanceOutcome.REJECTED)
                  .extracting(ChaosInstanceStatus::httpStatus)
                  .containsExactly(403);
            });
  }

  @Test
  void submit_whenAccessTokenCannotBeObtained_sendsNothingAndReportsUnreachable() {
    when(authorizedClientManager.authorize(any()))
        .thenThrow(
            new ClientAuthorizationException(
                new OAuth2Error("invalid_client"), HttpClientConfig.CHAOS_CLIENT_REGISTRATION));

    UUID id = submitAccepted();

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              ChaosCommandStatusResponse status = commandService.getStatus(id).orElseThrow();
              assertThat(status.status()).isEqualTo(CommandAggregateStatus.FAILED);
              assertThat(status.instances())
                  .extracting(ChaosInstanceStatus::outcome)
                  .containsOnly(InstanceOutcome.UNREACHABLE);
            });
    assertThat(pods.seen()).isEmpty();
  }

  @Test
  void submit_whenNoInstancesAreRegistered_isRejected() {
    ChaosCommandService.SubmitResult result =
        commandService.submit(enableRequest("chaos-poc-downstream"));

    assertThat(result).isInstanceOf(ChaosCommandService.SubmitResult.Unavailable.class);
    ChaosCommandService.SubmitResult.Unavailable unavailable =
        (ChaosCommandService.SubmitResult.Unavailable) result;
    assertThat(unavailable.response().status()).isEqualTo("NO_INSTANCES");
  }

  private UUID submitAccepted() {
    ChaosCommandService.SubmitResult result =
        commandService.submit(enableRequest("chaos-poc-demo"));
    assertThat(result).isInstanceOf(ChaosCommandService.SubmitResult.Accepted.class);
    return ((ChaosCommandService.SubmitResult.Accepted) result).response().commandId();
  }

  private static ChaosCommandRequest enableRequest(String target) {
    return new ChaosCommandRequest(
        null,
        "test",
        target,
        ChaosCommandAction.ENABLE,
        null,
        Instant.now().plusSeconds(60),
        "integration-test",
        "dispatch");
  }

  private static Response authorizedJson(Request request, String podName, String outcome)
      throws IOException {
    assertThat(request.headers().get(HttpHeaders.AUTHORIZATION))
        .containsExactly("Bearer " + ACCESS_TOKEN);
    return json(request, 200, resultJson(commandId(request), podName, outcome));
  }

  private static String commandId(Request request) throws IOException {
    return new JacksonConfiguration()
        .objectMapper()
        .readTree(new String(request.body(), StandardCharsets.UTF_8))
        .path("commandId")
        .asString();
  }

  private static Response json(Request request, int status, String body) {
    return Response.builder()
        .status(status)
        .reason("stub")
        .request(request)
        .headers(Map.of(HttpHeaders.CONTENT_TYPE, List.of(MediaType.APPLICATION_JSON_VALUE)))
        .body(body, StandardCharsets.UTF_8)
        .build();
  }

  private static String resultJson(String commandId, String podName, String outcome) {
    return new JacksonConfiguration()
        .objectMapper()
        .createObjectNode()
        .put("commandId", commandId)
        .put("targetApplication", "chaos-poc-demo")
        .put("podName", podName)
        .put("outcome", outcome)
        .put("reportedAt", "2026-10-05T12:00:00Z")
        .toString();
  }

  @TestConfiguration
  static class CommandFeignStub {

    @Bean
    RecordingFeignClient chaosCommandFeignClient() {
      return new RecordingFeignClient();
    }
  }

  static final class RecordingFeignClient implements Client {

    private final Map<String, IoHandler> handlers = new ConcurrentHashMap<>();
    private final List<Request> seen = new CopyOnWriteArrayList<>();

    void reset() {
      handlers.clear();
      seen.clear();
    }

    void whenUrl(String url, IoHandler handler) {
      handlers.put(url, handler);
    }

    List<Request> seen() {
      return List.copyOf(seen);
    }

    @Override
    public Response execute(Request request, Request.Options options) throws IOException {
      seen.add(request);
      IoHandler handler = handlers.remove(request.url());
      if (handler == null) {
        throw new IllegalStateException("No stub for " + request.url());
      }
      return handler.handle(request);
    }

    @FunctionalInterface
    interface IoHandler {
      Response handle(Request request) throws IOException;
    }
  }
}
