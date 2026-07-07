package com.samba.chaos.relay.model;

import java.util.List;

public record ValidationErrorResponse(String status, List<FieldError> errors) {

  public record FieldError(String field, String message) {}
}
