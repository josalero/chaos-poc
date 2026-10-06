package com.samba.chaos.relay.model;

import java.util.List;

/**
 * Body of a rejected command.
 *
 * <pre>
 * { "status": "REJECTED",
 *   "errors": [{ "field": "environment", "message": "must be test" }] }
 * </pre>
 *
 * <p>An empty registry uses the same shape with status {@code NO_INSTANCES}.
 */
public record ValidationErrorResponse(String status, List<FieldError> errors) {

  /** One field that failed validation, with the message returned to the client. */
  public record FieldError(String field, String message) {}
}
