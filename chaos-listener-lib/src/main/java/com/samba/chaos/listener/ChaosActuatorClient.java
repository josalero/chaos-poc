package com.samba.chaos.listener;

import com.samba.chaos.listener.message.ChaosAssaultConfig;
import com.samba.chaos.listener.message.ChaosCommandAction;
import com.samba.chaos.listener.exception.PermanentActuatorException;
import com.samba.chaos.listener.exception.TransientActuatorException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

public class ChaosActuatorClient {

  private static final int DEFAULT_LEVEL = 1;
  private static final int DEFAULT_LATENCY_RANGE_START = 1000;
  private static final int DEFAULT_LATENCY_RANGE_END = 3000;

  private final ChaosListenerProperties properties;
  private final RestTemplate restTemplate;
  private final RetryTemplate actuatorRetryTemplate;

  public ChaosActuatorClient(ChaosListenerProperties properties, RetryTemplate actuatorRetryTemplate) {
    this(properties, new RestTemplate(), actuatorRetryTemplate);
  }

  ChaosActuatorClient(
      ChaosListenerProperties properties,
      RestTemplate restTemplate,
      RetryTemplate actuatorRetryTemplate) {
    this.properties = properties;
    this.restTemplate = restTemplate;
    this.actuatorRetryTemplate = actuatorRetryTemplate;
  }

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

  public ActuatorStepResult enable() {
    return post("enable", Map.of());
  }

  public ActuatorStepResult disable() {
    return post("disable", Map.of());
  }

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

  private static Object resolveException(com.fasterxml.jackson.databind.JsonNode exception) {
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

  private ApplyResult singleStep(String step, java.util.function.Supplier<ActuatorStepResult> call) {
    ActuatorStepResult result = call.get();
    if (result.succeeded()) {
      return ApplyResult.ok();
    }
    return ApplyResult.failed(step, result.httpStatus());
  }

  private ActuatorStepResult post(String path, Object body) {
    try {
      return actuatorRetryTemplate.execute(context -> executePost(path, body));
    } catch (PermanentActuatorException ex) {
      return ActuatorStepResult.failed(ex.getHttpStatus());
    } catch (TransientActuatorException ex) {
      return ActuatorStepResult.failed(ex.getHttpStatus());
    }
  }

  private ActuatorStepResult executePost(String path, Object body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<Object> entity = new HttpEntity<>(body, headers);
    String url = properties.getActuatorBaseUrl() + "/" + path;
    try {
      restTemplate.postForEntity(url, entity, Void.class);
      return ActuatorStepResult.ok(200);
    } catch (HttpStatusCodeException ex) {
      int status = ex.getStatusCode().value();
      if (status == 503 || status == 504) {
        throw new TransientActuatorException(path, status, ex);
      }
      throw new PermanentActuatorException(path, status);
    } catch (ResourceAccessException ex) {
      throw new TransientActuatorException(path, 0, ex);
    }
  }

  public record ActuatorStepResult(boolean succeeded, int httpStatus) {

    static ActuatorStepResult ok(int httpStatus) {
      return new ActuatorStepResult(true, httpStatus);
    }

    static ActuatorStepResult failed(int httpStatus) {
      return new ActuatorStepResult(false, httpStatus);
    }
  }

  public record ApplyResult(boolean succeeded, String failedStep, int httpStatus) {

    static ApplyResult ok() {
      return new ApplyResult(true, null, 200);
    }

    static ApplyResult failed(String failedStep, int httpStatus) {
      return new ApplyResult(false, failedStep, httpStatus);
    }
  }
}
