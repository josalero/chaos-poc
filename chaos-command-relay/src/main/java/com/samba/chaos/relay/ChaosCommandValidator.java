package com.samba.chaos.relay;

import com.samba.chaos.listener.message.ChaosCommandAction;
import com.samba.chaos.listener.message.ChaosCommandMessage;
import com.samba.chaos.relay.model.ValidationErrorResponse;
import com.samba.chaos.relay.model.ValidationErrorResponse.FieldError;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ChaosCommandValidator {

  private final ChaosRelayProperties properties;

  public ChaosCommandValidator(ChaosRelayProperties properties) {
    this.properties = properties;
  }

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
        errors.add(
            new FieldError("expiresAt", "must be in the future for " + request.action()));
      }
    }

    if (request.assault() != null) {
      validateAssault(request.assault(), errors);
    }

    return errors;
  }

  private void validateAssault(
      com.samba.chaos.listener.message.ChaosAssaultConfig assault, List<FieldError> errors) {
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
    return action == ChaosCommandAction.ENABLE
        || action == ChaosCommandAction.CONFIGURE_AND_ENABLE;
  }

  public ValidationErrorResponse toErrorResponse(List<FieldError> errors) {
    return new ValidationErrorResponse("REJECTED", errors);
  }

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
