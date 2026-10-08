package com.samba.chaos.relay.model;

import java.util.List;
import java.util.UUID;

/**
 * Accepted selected reset. A service with errors was not published.
 *
 * <pre>
 * { "services": [
 *     { "applicationName": "chaos-poc-demo",
 *       "commandId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
 *       "statusUrl": "/internal/v1/chaos/commands/7c9e6679-7425-40de-944b-e07fc1f90ae7" }
 * ] }
 * </pre>
 *
 * @param services one entry per distinct selected name
 */
public record ChaosResetSelectionResponse(List<ServiceReset> services) {

  /** One selected service and the command the relay published, when it did. */
  public record ServiceReset(
      String applicationName,
      UUID commandId,
      String statusUrl,
      List<ValidationErrorResponse.FieldError> errors) {}
}
