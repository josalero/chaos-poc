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
 * Disable that waits until the aggregate is terminal, plus demo-data reset.
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
   * Resets each allowlisted service in order. One failure does not stop the rest.
   *
   * @param request issuer applied to every service
   * @return one outcome per allowlisted application
   */
  public ResetAllConfigurationResult resetAllConfigurations(ChaosMaintenanceRequest request) {
    List<ResetAllConfigurationResult.ServiceResetOutcome> outcomes = new ArrayList<>();
    String correlationBase =
        request.correlationId() != null && !request.correlationId().isBlank()
            ? request.correlationId()
            : "console-reset-all-" + Instant.now().toEpochMilli();

    for (String applicationName : relayProperties.getAllowedTargetApplications()) {
      ChaosMaintenanceRequest perServiceRequest =
          new ChaosMaintenanceRequest(
              request.issuedBy() != null && !request.issuedBy().isBlank()
                  ? request.issuedBy()
                  : "chaos-console",
              correlationBase + "-" + applicationName,
              request.expiresAt());
      outcomes.add(
          new ResetAllConfigurationResult.ServiceResetOutcome(
              applicationName, resetConfiguration(applicationName, perServiceRequest)));
    }
    return new ResetAllConfigurationResult(outcomes);
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

  /** One outcome per allowlisted application. */
  public record ResetAllConfigurationResult(List<ServiceResetOutcome> outcomes) {

    /** Application name paired with that service's reset result. */
    public record ServiceResetOutcome(String applicationName, ResetConfigurationResult result) {}

    /** How many services finished APPLIED. */
    public int successCount() {
      return (int)
          outcomes.stream()
              .filter(outcome -> outcome.result() instanceof ResetConfigurationResult.Success)
              .count();
    }

    /** True when every service finished APPLIED. */
    public boolean allSucceeded() {
      return !outcomes.isEmpty() && successCount() == outcomes.size();
    }

    /** Field errors for services that were rejected or did not finish APPLIED. */
    public List<FieldError> failedServiceErrors() {
      return outcomes.stream()
          .flatMap(
              outcome -> {
                ResetConfigurationResult result = outcome.result();
                if (result instanceof ResetConfigurationResult.Rejected rejected) {
                  return rejected.errors().stream()
                      .map(
                          error ->
                              new FieldError(
                                  outcome.applicationName() + "." + error.field(),
                                  error.message()));
                }
                if (result instanceof ResetConfigurationResult.CommandNotApplied failed) {
                  return java.util.stream.Stream.of(
                      new FieldError(
                          outcome.applicationName(),
                          "disable command finished with status "
                              + failed.status()
                              + " — see command "
                              + failed.commandId()));
                }
                return java.util.stream.Stream.empty();
              })
          .toList();
    }
  }
}
