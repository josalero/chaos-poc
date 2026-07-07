package com.samba.chaos.relay;

import com.samba.chaos.relay.model.ChaosCommandStatusResponse;
import com.samba.chaos.relay.model.ChaosCommandSubmitResponse;
import com.samba.chaos.relay.model.ChaosInstanceStatus;
import com.samba.chaos.relay.model.CommandAggregateStatus;
import com.samba.chaos.listener.message.InstanceOutcome;
import com.samba.chaos.relay.model.ValidationErrorResponse;
import com.samba.chaos.relay.model.ValidationErrorResponse.FieldError;
import com.samba.chaos.relay.InMemoryChaosCommandStore.CommandRecord;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ChaosCommandStatusService {

  private final ChaosCommandStore commandStore;
  private final ChaosRelayProperties properties;

  public ChaosCommandStatusService(
      ChaosCommandStore commandStore, ChaosRelayProperties properties) {
    this.commandStore = commandStore;
    this.properties = properties;
  }

  public CommandAggregateStatus aggregateStatus(CommandRecord record) {
    int reported = record.instances().size();
    int expected = record.expectedInstances();

    if (record.failureCount() > 0) {
      return CommandAggregateStatus.FAILED;
    }

    if (reported >= expected && record.successCount() >= expected) {
      return CommandAggregateStatus.APPLIED;
    }

    Instant timeoutAt =
        record.publishedAt().plusSeconds(properties.getStatusTimeoutSeconds());
    if (Instant.now().isAfter(timeoutAt)) {
      return CommandAggregateStatus.TIMED_OUT;
    }

    if (reported == 0) {
      return CommandAggregateStatus.PENDING;
    }

    return CommandAggregateStatus.PARTIAL;
  }

  public ChaosCommandStatusResponse toStatusResponse(CommandRecord record) {
    CommandAggregateStatus status = aggregateStatus(record);
    Instant completedAt = isTerminal(status) ? Instant.now() : null;

    return new ChaosCommandStatusResponse(
        record.commandId(),
        status,
        record.targetApplication(),
        record.action(),
        record.expectedInstances(),
        record.successCount(),
        record.failureCount(),
        record.publishedAt(),
        completedAt,
        record.correlationId(),
        record.instances());
  }

  public ChaosCommandSubmitResponse toSubmitResponse(CommandRecord record) {
    return new ChaosCommandSubmitResponse(
        record.commandId(),
        CommandAggregateStatus.PUBLISHED,
        record.publishedAt(),
        record.targetApplication(),
        record.expectedInstances(),
        "/internal/v1/chaos/commands/" + record.commandId(),
        record.correlationId());
  }

  private boolean isTerminal(CommandAggregateStatus status) {
    return status == CommandAggregateStatus.APPLIED
        || status == CommandAggregateStatus.FAILED
        || status == CommandAggregateStatus.TIMED_OUT;
  }
}
