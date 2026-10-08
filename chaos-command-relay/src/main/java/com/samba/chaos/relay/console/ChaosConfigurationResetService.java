package com.samba.chaos.relay.console;

import com.samba.chaos.relay.config.ChaosRelayProperties;
import com.samba.chaos.relay.model.ChaosMaintenanceRequest;
import com.samba.chaos.relay.model.CommandAggregateStatus;
import com.samba.chaos.relay.model.ValidationErrorResponse.FieldError;
import com.samba.chaos.relay.service.ChaosCommandStatusService;
import com.samba.chaos.relay.service.ChaosServiceMaintenanceService;
import com.samba.chaos.relay.store.ChaosCommandStore;
import com.samba.chaos.relay.store.CommandRecord;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Per-service disable that waits until the aggregate is terminal, plus a selected reset that
 * returns as soon as the commands are published.
 *
 * <p>Success is only {@code APPLIED}. {@code FAILED} and {@code TIMED_OUT} are command-not-applied.
 * The wait polls every 500 ms until {@code chaos.relay.status-timeout-seconds}.
 */
@Service
public class ChaosConfigurationResetService {

  private final ChaosServiceMaintenanceService maintenanceService;
  private final ChaosCommandStore commandStore;
  private final ChaosCommandStatusService commandStatusService;
  private final ChaosDemoAdminClient demoAdminClient;
  private final ChaosRelayProperties relayProperties;

  /**
   * Creates the reset service.
   *
   * @param maintenanceService publishes DISABLE
   * @param commandStore status reads while waiting
   * @param commandStatusService aggregate computation
   * @param demoAdminClient demo order reset
   * @param relayProperties allowlist and status timeout
   */
  public ChaosConfigurationResetService(
      ChaosServiceMaintenanceService maintenanceService,
      ChaosCommandStore commandStore,
      ChaosCommandStatusService commandStatusService,
      ChaosDemoAdminClient demoAdminClient,
      ChaosRelayProperties relayProperties) {
    this.maintenanceService = maintenanceService;
    this.commandStore = commandStore;
    this.commandStatusService = commandStatusService;
    this.demoAdminClient = demoAdminClient;
    this.relayProperties = relayProperties;
  }

  /**
   * Publishes DISABLE and blocks until APPLIED, FAILED, or TIMED_OUT.
   *
   * <p>A blank {@code issuedBy} becomes {@code chaos-console}.
   *
   * @param applicationName allowlisted Eureka application name
   * @param request issuer and optional correlation id
   * @return success, rejected, or command-not-applied
   */
  public ResetConfigurationResult resetConfiguration(
      String applicationName, ChaosMaintenanceRequest request) {
    ChaosMaintenanceRequest disableRequest =
        new ChaosMaintenanceRequest(
            request.issuedBy() != null && !request.issuedBy().isBlank()
                ? request.issuedBy()
                : "chaos-console",
            request.correlationId() != null && !request.correlationId().isBlank()
                ? request.correlationId()
                : "console-reset-" + Instant.now().toEpochMilli(),
            null);

    ChaosServiceMaintenanceService.MaintenanceResult disableResult =
        maintenanceService.disable(applicationName, disableRequest);

    if (disableResult
        instanceof ChaosServiceMaintenanceService.MaintenanceResult.Rejected rejected) {
      return ResetConfigurationResult.rejected(rejected.errors());
    }

    UUID commandId =
        ((ChaosServiceMaintenanceService.MaintenanceResult.Accepted) disableResult)
            .response()
            .commandId();

    CommandAggregateStatus terminalStatus = awaitTerminalStatus(commandId);
    if (terminalStatus != CommandAggregateStatus.APPLIED) {
      return ResetConfigurationResult.commandNotApplied(commandId, terminalStatus);
    }

    return ResetConfigurationResult.success(commandId);
  }

  /**
   * Clears demo orders. The Chaos Monkey assault is left unchanged.
   *
   * @param applicationName Eureka application name
   * @return success, not-configured when none are UP, or failed when a call errors
   */
  public ClearDemoDataResult clearDemoData(String applicationName) {
    if (!relayProperties.getAllowedTargetApplications().contains(applicationName)) {
      return new ClearDemoDataResult.NotConfigured();
    }

    try {
      return demoAdminClient.resetDemoData(applicationName)
          ? new ClearDemoDataResult.Success()
          : new ClearDemoDataResult.NotConfigured();
    } catch (IllegalStateException ex) {
      return new ClearDemoDataResult.Failed(ex.getMessage());
    }
  }

  /**
   * Publishes a disable command for each selected name and returns immediately. An empty list or
   * any name outside the allowlist publishes nothing.
   *
   * @param issuedBy operator name stored on each command
   * @param applicationNames selected services, duplicates ignored
   * @return a rejection, or one outcome per distinct name
   */
  public ResetSelectionResult resetSelected(String issuedBy, List<String> applicationNames) {
    List<String> names =
        applicationNames == null ? List.of() : applicationNames.stream().distinct().toList();
    if (names.isEmpty()) {
      return ResetSelectionResult.rejected(
          List.of(new FieldError("applicationNames", "at least one service is required")));
    }
    for (String name : names) {
      if (!relayProperties.getAllowedTargetApplications().contains(name)) {
        return ResetSelectionResult.rejected(
            List.of(new FieldError("applicationNames", name + " is not allowlisted")));
      }
    }
    String issuer = issuedBy != null && !issuedBy.isBlank() ? issuedBy : "chaos-console";
    List<ResetSelectionResult.ServiceReset> services = new ArrayList<>();
    for (String name : names) {
      ChaosServiceMaintenanceService.MaintenanceResult result =
          maintenanceService.disable(name, new ChaosMaintenanceRequest(issuer, null, null));
      if (result instanceof ChaosServiceMaintenanceService.MaintenanceResult.Rejected rejected) {
        services.add(new ResetSelectionResult.ServiceReset(name, null, null, rejected.errors()));
      } else if (result
          instanceof ChaosServiceMaintenanceService.MaintenanceResult.Accepted accepted) {
        services.add(
            new ResetSelectionResult.ServiceReset(
                name, accepted.response().commandId(), accepted.response().statusUrl(), List.of()));
      }
    }
    return new ResetSelectionResult.Accepted(services);
  }

  private CommandAggregateStatus awaitTerminalStatus(UUID commandId) {
    Duration timeout = Duration.ofSeconds(relayProperties.getStatusTimeoutSeconds());
    Instant deadline = Instant.now().plus(timeout);

    while (Instant.now().isBefore(deadline)) {
      Optional<CommandRecord> record = commandStore.findById(commandId);
      if (record.isPresent()) {
        CommandAggregateStatus status = commandStatusService.aggregateStatus(record.get());
        if (isTerminal(status)) {
          return status;
        }
      }
      sleepBriefly();
    }

    return commandStore
        .findById(commandId)
        .map(commandStatusService::aggregateStatus)
        .orElse(CommandAggregateStatus.TIMED_OUT);
  }

  private static boolean isTerminal(CommandAggregateStatus status) {
    return status == CommandAggregateStatus.APPLIED
        || status == CommandAggregateStatus.FAILED
        || status == CommandAggregateStatus.TIMED_OUT;
  }

  private static void sleepBriefly() {
    try {
      Thread.sleep(500L);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for chaos command", ex);
    }
  }

  /** Outcome of one service reset before the controller maps it to HTTP. */
  public sealed interface ResetConfigurationResult {
    /** DISABLE finished APPLIED. Maps to HTTP 200. */
    record Success(UUID commandId) implements ResetConfigurationResult {}

    /** The command was rejected before fan-out. Maps to HTTP 400. */
    record Rejected(List<FieldError> errors) implements ResetConfigurationResult {}

    /** The aggregate finished FAILED or TIMED_OUT. Maps to HTTP 409. */
    record CommandNotApplied(UUID commandId, CommandAggregateStatus status)
        implements ResetConfigurationResult {}

    /**
     * Returns the applied outcome.
     *
     * @param commandId DISABLE command id
     * @return success result
     */
    static ResetConfigurationResult success(UUID commandId) {
      return new Success(commandId);
    }

    /**
     * Returns the validation outcome.
     *
     * @param errors field errors
     * @return rejected result
     */
    static ResetConfigurationResult rejected(List<FieldError> errors) {
      return new Rejected(errors);
    }

    /**
     * Returns the non-applied outcome.
     *
     * @param commandId DISABLE command id
     * @param status terminal aggregate, FAILED or TIMED_OUT
     * @return command-not-applied result
     */
    static ResetConfigurationResult commandNotApplied(
        UUID commandId, CommandAggregateStatus status) {
      return new CommandNotApplied(commandId, status);
    }
  }

  /** Outcome of clearing demo orders on one application. */
  public sealed interface ClearDemoDataResult {
    /** Every UP instance accepted the admin reset. */
    record Success() implements ClearDemoDataResult {}

    /** The name is not allowlisted, or no instance is UP. */
    record NotConfigured() implements ClearDemoDataResult {}

    /** An instance rejected or dropped the admin reset. */
    record Failed(String message) implements ClearDemoDataResult {}
  }

  /** Outcome of a selected reset that does not wait for pods. */
  public sealed interface ResetSelectionResult {

    /** Nothing was published. */
    record Rejected(List<FieldError> errors) implements ResetSelectionResult {}

    /** One entry per selected name. A missing command id means that name was not published. */
    record Accepted(List<ServiceReset> services) implements ResetSelectionResult {}

    /** One selected service. */
    record ServiceReset(
        String applicationName, UUID commandId, String statusUrl, List<FieldError> errors) {}

    /**
     * Rejects the whole selection.
     *
     * @param errors why nothing was published
     * @return rejected result
     */
    static ResetSelectionResult rejected(List<FieldError> errors) {
      return new Rejected(errors);
    }
  }
}
