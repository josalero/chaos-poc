package com.samba.chaos.relay.service;

import com.samba.chaos.command.ChaosCommandAction;
import com.samba.chaos.command.ChaosCommandMessage;
import com.samba.chaos.relay.config.ChaosRelayProperties;
import com.samba.chaos.relay.model.ChaosCommandRequest;
import com.samba.chaos.relay.model.InstanceSelection;
import com.samba.chaos.relay.model.ValidationErrorResponse;
import com.samba.chaos.relay.model.ValidationErrorResponse.FieldError;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Field rules for a command before it is stored.
 *
 * <p>{@code environment} must be {@code test}. The target must be allowlisted. {@code assault} is
 * required for {@code CONFIGURE} and {@code CONFIGURE_AND_ENABLE}. {@code expiresAt} must be in the
 * future for {@code ENABLE} and {@code CONFIGURE_AND_ENABLE}. {@code SOME} requires instance ids.
 * {@code ALL} rejects instance ids.
 */
@Component
public class ChaosCommandValidator {

  private final ChaosRelayProperties properties;

  /**
   * Creates the validator.
   *
   * @param properties allowlist of target application names
   */
  public ChaosCommandValidator(ChaosRelayProperties properties) {
    this.properties = properties;
  }

  /**
   * Collects every field error. An empty list means the request may be stored.
   *
   * @param request command body
   * @return field errors, empty when the request is acceptable
   */
  public List<FieldError> validate(ChaosCommandRequest request) {
    List<FieldError> errors = new ArrayList<>();

    if (!"test".equals(request.environment())) {
      errors.add(new FieldError("environment", "must be 'test'"));
    }

    if (!properties.getAllowedTargetApplications().contains(request.targetApplication())) {
      errors.add(new FieldError("targetApplication", "not in allowed-target-applications"));
    }

    if (requiresAssault(request.action()) && request.assault() == null) {
      errors.add(new FieldError("assault", "required for action " + request.action()));
    }

    if (requiresExpiry(request.action())) {
      if (request.expiresAt() == null) {
        errors.add(new FieldError("expiresAt", "required for action " + request.action()));
      } else if (!request.expiresAt().isAfter(Instant.now())) {
        errors.add(new FieldError("expiresAt", "must be in the future for " + request.action()));
      }
    }

    if (request.assault() != null) {
      validateAssault(request.assault(), errors);
    }

    validateSelection(request, errors);

    return errors;
  }

  private void validateSelection(ChaosCommandRequest request, List<FieldError> errors) {
    InstanceSelection selection =
        request.instanceSelection() == null ? InstanceSelection.ALL : request.instanceSelection();
    List<String> instanceIds = request.instanceIds() == null ? List.of() : request.instanceIds();
    if (selection == InstanceSelection.ALL) {
      if (!instanceIds.isEmpty()) {
        errors.add(new FieldError("instanceIds", "omit instanceIds when instanceSelection is ALL"));
      }
      return;
    }

    if (instanceIds.isEmpty()) {
      errors.add(new FieldError("instanceIds", "required when instanceSelection is SOME"));
      return;
    }

    Set<String> seen = new HashSet<>();
    for (String instanceId : instanceIds) {
      if (instanceId == null || instanceId.isBlank()) {
        errors.add(new FieldError("instanceIds", "instance id must not be blank"));
        return;
      }
      if (!seen.add(instanceId)) {
        errors.add(new FieldError("instanceIds", "duplicate instance id " + instanceId));
        return;
      }
    }
  }

  private void validateAssault(
      com.samba.chaos.command.ChaosAssaultConfig assault, List<FieldError> errors) {
    if (assault.level() != null && (assault.level() < 1 || assault.level() > 10000)) {
      errors.add(new FieldError("assault.level", "must be between 1 and 10000"));
    }
    if (Boolean.TRUE.equals(assault.latencyActive())) {
      if (assault.latencyRangeStart() == null || assault.latencyRangeEnd() == null) {
        errors.add(
            new FieldError(
                "assault.latencyRange", "latencyRangeStart and latencyRangeEnd required"));
      } else if (assault.latencyRangeStart() > assault.latencyRangeEnd()) {
        errors.add(new FieldError("assault.latencyRange", "start must be <= end"));
      }
    }
  }

  private boolean requiresAssault(ChaosCommandAction action) {
    return action == ChaosCommandAction.CONFIGURE
        || action == ChaosCommandAction.CONFIGURE_AND_ENABLE;
  }

  private boolean requiresExpiry(ChaosCommandAction action) {
    return action == ChaosCommandAction.ENABLE || action == ChaosCommandAction.CONFIGURE_AND_ENABLE;
  }

  /**
   * Wraps field errors in the body returned for HTTP 400.
   *
   * @param errors validation failures
   * @return response whose status is {@code REJECTED}
   */
  public ValidationErrorResponse toErrorResponse(List<FieldError> errors) {
    return new ValidationErrorResponse("REJECTED", errors);
  }

  /**
   * Copies the request into the body posted to each instance.
   *
   * @param request accepted command
   * @param commandId id stored by the relay, generated when the request omitted one
   * @return the chaos-lib message
   */
  public ChaosCommandMessage toMessage(ChaosCommandRequest request, UUID commandId) {
    return new ChaosCommandMessage(
        commandId,
        request.environment(),
        request.targetApplication(),
        request.action(),
        request.assault(),
        request.expiresAt(),
        request.issuedBy(),
        request.correlationId());
  }
}
