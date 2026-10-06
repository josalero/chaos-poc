package com.samba.chaos.relay.service;

import com.samba.chaos.command.ChaosAssaultConfig;
import com.samba.chaos.command.ChaosCommandAction;
import com.samba.chaos.relay.model.ChaosCommandRequest;
import com.samba.chaos.relay.model.ChaosCommandSubmitResponse;
import com.samba.chaos.relay.model.ChaosMaintenanceRequest;
import com.samba.chaos.relay.model.CommandAggregateStatus;
import com.samba.chaos.relay.model.ValidationErrorResponse.FieldError;
import com.samba.chaos.relay.store.ChaosCommandStore;
import com.samba.chaos.relay.store.CommandRecord;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Enable and disable shortcuts over {@link ChaosCommandService#submit}.
 *
 * <p>Enable replays the newest applied {@code CONFIGURE} or {@code CONFIGURE_AND_ENABLE} assault.
 * Disable sends no assault. Both return as soon as the command is published.
 */
@Service
public class ChaosServiceMaintenanceService {

  private final ChaosCommandService commandService;
  private final ChaosCommandStore commandStore;
  private final ChaosCommandStatusService statusService;

  /**
   * Creates the maintenance service.
   *
   * @param commandService command submit
   * @param commandStore history used to find the last applied assault
   * @param statusService decides which prior command counts as applied
   */
  public ChaosServiceMaintenanceService(
      ChaosCommandService commandService,
      ChaosCommandStore commandStore,
      ChaosCommandStatusService statusService) {
    this.commandService = commandService;
    this.commandStore = commandStore;
    this.statusService = statusService;
  }

  /**
   * Publishes {@code ENABLE} for the last applied assault.
   *
   * <pre>
   * enable("chaos-poc-demo", { issuedBy, expiresAt })
   *   -&gt; Accepted when an APPLIED configure exists and expiresAt is in the future
   *   -&gt; Rejected field "assault" when no such command exists
   *   -&gt; Rejected field "expiresAt" when the lease is missing
   * </pre>
   *
   * @param applicationName allowlisted Eureka application name
   * @param request issuer and required lease
   * @return accepted or rejected
   */
  public MaintenanceResult enable(String applicationName, ChaosMaintenanceRequest request) {
    Optional<ChaosAssaultConfig> assault = findConfiguredAssault(applicationName);
    if (assault.isEmpty()) {
      return MaintenanceResult.rejected(
          List.of(
              new FieldError(
                  "assault",
                  "no prior CONFIGURE or CONFIGURE_AND_ENABLE found — use POST .../commands")));
    }
    if (request.expiresAt() == null) {
      return MaintenanceResult.rejected(
          List.of(new FieldError("expiresAt", "required for ENABLE")));
    }

    return submit(applicationName, ChaosCommandAction.ENABLE, assault.get(), request);
  }

  /**
   * Publishes {@code DISABLE} with no assault and no lease.
   *
   * @param applicationName allowlisted Eureka application name
   * @param request issuer
   * @return accepted, or rejected when discovery has no UP instances
   */
  public MaintenanceResult disable(String applicationName, ChaosMaintenanceRequest request) {
    return submit(applicationName, ChaosCommandAction.DISABLE, null, request);
  }

  /**
   * Same publish as {@link #disable}. Callers that must wait use {@code
   * ChaosConfigurationResetService}.
   *
   * @param applicationName allowlisted Eureka application name
   * @param request issuer
   * @return the disable result
   */
  public MaintenanceResult reset(String applicationName, ChaosMaintenanceRequest request) {
    return disable(applicationName, request);
  }

  private MaintenanceResult submit(
      String applicationName,
      ChaosCommandAction action,
      ChaosAssaultConfig assault,
      ChaosMaintenanceRequest request) {
    ChaosCommandRequest commandRequest =
        new ChaosCommandRequest(
            null,
            "test",
            applicationName,
            action,
            assault,
            request.expiresAt(),
            request.issuedBy(),
            request.correlationId());

    ChaosCommandService.SubmitResult result = commandService.submit(commandRequest);
    if (result instanceof ChaosCommandService.SubmitResult.Accepted accepted) {
      return MaintenanceResult.accepted(accepted.response());
    }
    if (result instanceof ChaosCommandService.SubmitResult.Rejected rejected) {
      return MaintenanceResult.rejected(rejected.response().errors());
    }
    if (result instanceof ChaosCommandService.SubmitResult.Unavailable unavailable) {
      return MaintenanceResult.rejected(unavailable.response().errors());
    }
    throw new IllegalStateException("Unknown submit result: " + result);
  }

  private Optional<ChaosAssaultConfig> findConfiguredAssault(String applicationName) {
    return commandStore.findAll().stream()
        .filter(record -> applicationName.equals(record.targetApplication()))
        .filter(
            record ->
                record.action() == ChaosCommandAction.CONFIGURE
                    || record.action() == ChaosCommandAction.CONFIGURE_AND_ENABLE)
        .filter(record -> statusService.aggregateStatus(record) == CommandAggregateStatus.APPLIED)
        .max(Comparator.comparing(CommandRecord::publishedAt))
        .map(CommandRecord::assault);
  }

  /** Enable or disable outcome before the controller maps it to HTTP. */
  public sealed interface MaintenanceResult {
    /** The command was published. Maps to HTTP 202. */
    record Accepted(ChaosCommandSubmitResponse response) implements MaintenanceResult {}

    /** Validation or discovery rejected the command. Maps to HTTP 400. */
    record Rejected(List<FieldError> errors) implements MaintenanceResult {}

    /**
     * Returns the published outcome.
     *
     * @param response submit body
     * @return accepted result
     */
    static MaintenanceResult accepted(ChaosCommandSubmitResponse response) {
      return new Accepted(response);
    }

    /**
     * Returns the rejected outcome.
     *
     * @param errors field errors
     * @return rejected result
     */
    static MaintenanceResult rejected(List<FieldError> errors) {
      return new Rejected(errors);
    }
  }
}
