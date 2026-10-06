package com.samba.chaos.service;

import com.samba.chaos.command.ChaosAssaultConfig;
import com.samba.chaos.command.ChaosCommandAction;
import com.samba.chaos.config.ChaosProperties;
import com.samba.chaos.exception.PermanentActuatorException;
import com.samba.chaos.exception.TransientActuatorException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

/** Calls the local Chaos Monkey actuator and retries transient failures. */
public class ChaosActuatorClient {

  private static final int DEFAULT_LEVEL = 1;
  private static final int DEFAULT_LATENCY_RANGE_START = 1000;
  private static final int DEFAULT_LATENCY_RANGE_END = 3000;

  private final ChaosProperties properties;
  private final RestClient restClient;
  private final RetryTemplate actuatorRetryTemplate;

  /** Creates a client that posts to {@code samba.chaos.command.actuator-base-url}. */
  public ChaosActuatorClient(ChaosProperties properties, RetryTemplate actuatorRetryTemplate) {
    this(properties, RestClient.create(), actuatorRetryTemplate);
  }

  ChaosActuatorClient(
      ChaosProperties properties, RestClient restClient, RetryTemplate actuatorRetryTemplate) {
    this.properties = properties;
    this.restClient = restClient;
    this.actuatorRetryTemplate = actuatorRetryTemplate;
  }

  /** Posts the assault configuration, filling omitted fields with Chaos Monkey defaults. */
  public ActuatorStepResult configureAssaults(ChaosAssaultConfig assault) {
    Map<String, Object> body = new HashMap<>();
    body.put("level", assault.level() != null ? assault.level() : DEFAULT_LEVEL);
    body.put("deterministic", assault.deterministic() != null ? assault.deterministic() : true);
    body.put("latencyActive", Boolean.TRUE.equals(assault.latencyActive()));
    body.put("exceptionsActive", Boolean.TRUE.equals(assault.exceptionsActive()));
    body.put(
        "latencyRangeStart",
        assault.latencyRangeStart() != null
            ? assault.latencyRangeStart()
            : DEFAULT_LATENCY_RANGE_START);
    body.put(
        "latencyRangeEnd",
        assault.latencyRangeEnd() != null ? assault.latencyRangeEnd() : DEFAULT_LATENCY_RANGE_END);
    body.put(
        "watchedCustomServices",
        assault.watchedCustomServices() != null ? assault.watchedCustomServices() : List.of());
    body.put("exception", resolveException(assault.exception()));
    return post("assaults", body);
  }

  /** Enables Chaos Monkey. */
  public ActuatorStepResult enable() {
    return post("enable", Map.of());
  }

  /** Disables Chaos Monkey. */
  public ActuatorStepResult disable() {
    return post("disable", Map.of());
  }

  /** Restores the assault configuration to the library defaults. */
  public ActuatorStepResult resetAssaults() {
    Map<String, Object> body = new HashMap<>();
    body.put("level", DEFAULT_LEVEL);
    body.put("deterministic", true);
    body.put("latencyActive", false);
    body.put("latencyRangeStart", DEFAULT_LATENCY_RANGE_START);
    body.put("latencyRangeEnd", DEFAULT_LATENCY_RANGE_END);
    body.put("exceptionsActive", false);
    body.put("exception", defaultRuntimeException());
    body.put("watchedCustomServices", List.of());
    return post("assaults", body);
  }

  /** Runs the actuator steps for one command action. Steps stop at the first failure. */
  public synchronized ApplyResult apply(ChaosCommandAction action, ChaosAssaultConfig assault) {
    return switch (action) {
      case CONFIGURE -> {
        ActuatorStepResult reset = resetAssaults();
        if (!reset.succeeded()) {
          yield ApplyResult.failed("assaults", reset.httpStatus());
        }
        yield singleStep("assaults", () -> configureAssaults(assault));
      }
      case ENABLE -> singleStep("enable", this::enable);
      case DISABLE -> {
        ActuatorStepResult reset = resetAssaults();
        if (!reset.succeeded()) {
          yield ApplyResult.failed("assaults", reset.httpStatus());
        }
        ActuatorStepResult disable = disable();
        if (!disable.succeeded()) {
          yield ApplyResult.failed("disable", disable.httpStatus());
        }
        yield ApplyResult.ok();
      }
      case CONFIGURE_AND_ENABLE -> {
        ActuatorStepResult reset = resetAssaults();
        if (!reset.succeeded()) {
          yield ApplyResult.failed("assaults", reset.httpStatus());
        }
        ActuatorStepResult configure = configureAssaults(assault);
        if (!configure.succeeded()) {
          yield ApplyResult.failed("assaults", configure.httpStatus());
        }
        ActuatorStepResult enable = enable();
        if (!enable.succeeded()) {
          yield ApplyResult.failed("enable", enable.httpStatus());
        }
        yield ApplyResult.ok();
      }
    };
  }

  private static Object resolveException(JsonNode exception) {
    if (exception != null && !exception.isNull() && !exception.isMissingNode()) {
      return exception;
    }
    return defaultRuntimeException();
  }

  private static Map<String, Object> defaultRuntimeException() {
    return Map.of(
        "type",
        "java.lang.RuntimeException",
        "method",
        "<init>",
        "arguments",
        List.of(Map.of("type", "java.lang.String", "value", "Chaos Monkey - RuntimeException")));
  }

  private ApplyResult singleStep(String step, Supplier<ActuatorStepResult> call) {
    ActuatorStepResult result = call.get();
    if (result.succeeded()) {
      return ApplyResult.ok();
    }
    return ApplyResult.failed(step, result.httpStatus());
  }

  private ActuatorStepResult post(String path, Object body) {
    try {
      return actuatorRetryTemplate.execute(() -> executePost(path, body));
    } catch (RetryException exhausted) {
      return ActuatorStepResult.failed(statusOf(exhausted.getCause()));
    }
  }

  private ActuatorStepResult executePost(String path, Object body) {
    String url = properties.getActuatorBaseUrl() + "/" + path;
    try {
      restClient
          .post()
          .uri(url)
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .toBodilessEntity();
      return ActuatorStepResult.ok(200);
    } catch (RestClientResponseException ex) {
      int status = ex.getStatusCode().value();
      if (status == 503 || status == 504) {
        throw new TransientActuatorException(path, status, ex);
      }
      throw new PermanentActuatorException(path, status, ex);
    } catch (ResourceAccessException ex) {
      throw new TransientActuatorException(path, 0, ex);
    }
  }

  private static int statusOf(Throwable cause) {
    if (cause instanceof TransientActuatorException transientFailure) {
      return transientFailure.getHttpStatus();
    }
    if (cause instanceof PermanentActuatorException permanentFailure) {
      return permanentFailure.getHttpStatus();
    }
    return 0;
  }

  /** Outcome of one actuator HTTP call. */
  public record ActuatorStepResult(boolean succeeded, int httpStatus) {

    static ActuatorStepResult ok(int httpStatus) {
      return new ActuatorStepResult(true, httpStatus);
    }

    static ActuatorStepResult failed(int httpStatus) {
      return new ActuatorStepResult(false, httpStatus);
    }
  }

  /** Outcome of a full command action, including the actuator step that failed. */
  public record ApplyResult(boolean succeeded, String failedStep, int httpStatus) {

    static ApplyResult ok() {
      return new ApplyResult(true, null, 200);
    }

    static ApplyResult failed(String failedStep, int httpStatus) {
      return new ApplyResult(false, failedStep, httpStatus);
    }
  }
}
