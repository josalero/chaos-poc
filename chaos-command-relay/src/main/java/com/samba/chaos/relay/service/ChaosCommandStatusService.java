package com.samba.chaos.relay.service;

import com.samba.chaos.relay.config.ChaosRelayProperties;
import com.samba.chaos.relay.model.ChaosCommandStatusResponse;
import com.samba.chaos.relay.model.ChaosCommandSubmitResponse;
import com.samba.chaos.relay.model.CommandAggregateStatus;
import com.samba.chaos.relay.store.ChaosCommandStore;
import com.samba.chaos.relay.store.CommandRecord;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * Computes the aggregate from stored instance results.
 *
 * <p>Evaluation order is fixed: any failure is {@code FAILED}; every expected instance {@code
 * SUCCESS} is {@code APPLIED}; a closed status window is {@code TIMED_OUT}; zero reports is {@code
 * PENDING}; otherwise {@code PARTIAL}.
 */
@Service
public class ChaosCommandStatusService {

  private final ChaosCommandStore commandStore;
  private final ChaosRelayProperties properties;

  /**
   * Creates the status service.
   *
   * @param commandStore stored commands
   * @param properties supplies {@code status-timeout-seconds}
   */
  public ChaosCommandStatusService(
      ChaosCommandStore commandStore, ChaosRelayProperties properties) {
    this.commandStore = commandStore;
    this.properties = properties;
  }

  /**
   * Derives the aggregate for one stored command.
   *
   * <pre>
   * 2 expected, 2 SUCCESS, inside the window -&gt; APPLIED
   * 2 expected, 1 SUCCESS, inside the window -&gt; PARTIAL
   * 2 expected, 0 reports, past the window  -&gt; TIMED_OUT
   * any non-SUCCESS result                   -&gt; FAILED
   * </pre>
   *
   * @param record stored command and instance results
   * @return the aggregate for this instant
   */
  public CommandAggregateStatus aggregateStatus(CommandRecord record) {
    int reported = record.instances().size();
    int expected = record.expectedInstances();

    if (record.failureCount() > 0) {
      return CommandAggregateStatus.FAILED;
    }

    if (reported >= expected && record.successCount() >= expected) {
      return CommandAggregateStatus.APPLIED;
    }

    Instant timeoutAt = record.publishedAt().plusSeconds(properties.getStatusTimeoutSeconds());
    if (Instant.now().isAfter(timeoutAt)) {
      return CommandAggregateStatus.TIMED_OUT;
    }

    if (reported == 0) {
      return CommandAggregateStatus.PENDING;
    }

    return CommandAggregateStatus.PARTIAL;
  }

  /**
   * Maps a record to the status API, including assault fields the console renders as YAML.
   *
   * @param record stored command
   * @return status body; {@code completedAt} is set only for a terminal aggregate
   */
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
        record.issuedBy(),
        record.expiresAt(),
        record.assault(),
        record.instances(),
        record.instanceSelection(),
        record.instanceIds());
  }

  /**
   * Maps a newly stored record to the 202 body. Status is always {@code PUBLISHED}.
   *
   * @param record command just saved
   * @return submit body whose {@code statusUrl} is {@code /internal/v1/chaos/commands/{id}}
   */
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
