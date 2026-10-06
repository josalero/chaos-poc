package com.samba.chaos.relay.model;

import java.util.List;
import java.util.UUID;

/**
 * Result of resetting every allowlisted service. HTTP status stays 200 when some fail.
 *
 * <pre>
 * { "successCount": 1, "totalCount": 2,
 *   "services": [
 *     { "applicationName": "chaos-poc-demo", "commandStatus": "APPLIED" }
 *   ] }
 * </pre>
 */
public record ChaosConfigurationResetAllResponse(
    int successCount, int totalCount, List<ServiceResetOutcome> services) {

  /** One allowlisted service and the terminal status of its DISABLE command. */
  public record ServiceResetOutcome(
      String applicationName,
      UUID commandId,
      CommandAggregateStatus commandStatus,
      List<ValidationErrorResponse.FieldError> errors) {}
}
