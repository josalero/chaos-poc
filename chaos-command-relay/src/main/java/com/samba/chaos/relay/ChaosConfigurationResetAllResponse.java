package com.samba.chaos.relay;

import com.samba.chaos.relay.model.CommandAggregateStatus;
import com.samba.chaos.relay.model.ValidationErrorResponse.FieldError;
import java.util.List;
import java.util.UUID;

public record ChaosConfigurationResetAllResponse(
    int successCount, int totalCount, List<ServiceResetOutcome> services) {

  public record ServiceResetOutcome(
      String applicationName,
      UUID commandId,
      CommandAggregateStatus commandStatus,
      List<FieldError> errors) {}
}
