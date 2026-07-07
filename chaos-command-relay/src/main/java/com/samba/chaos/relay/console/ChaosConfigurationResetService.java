package com.samba.chaos.relay.console;

import com.samba.chaos.relay.model.ChaosMaintenanceRequest;
import com.samba.chaos.relay.model.CommandAggregateStatus;
import com.samba.chaos.relay.model.ValidationErrorResponse.FieldError;
import com.samba.chaos.relay.ChaosCommandStatusService;
import com.samba.chaos.relay.ChaosCommandStore;
import com.samba.chaos.relay.ChaosRelayProperties;
import com.samba.chaos.relay.ChaosServiceMaintenanceService;
import com.samba.chaos.relay.InMemoryChaosCommandStore.CommandRecord;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ChaosConfigurationResetService {

  private final ChaosServiceMaintenanceService maintenanceService;
  private final ChaosCommandStore commandStore;
  private final ChaosCommandStatusService commandStatusService;
  private final ChaosDemoAdminClient demoAdminClient;
  private final ChaosRelayProperties relayProperties;

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

    if (disableResult instanceof ChaosServiceMaintenanceService.MaintenanceResult.Rejected rejected) {
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

  public sealed interface ResetConfigurationResult {
    record Success(UUID commandId) implements ResetConfigurationResult {}

    record Rejected(List<FieldError> errors) implements ResetConfigurationResult {}

    record CommandNotApplied(UUID commandId, CommandAggregateStatus status)
        implements ResetConfigurationResult {}

    static ResetConfigurationResult success(UUID commandId) {
      return new Success(commandId);
    }

    static ResetConfigurationResult rejected(List<FieldError> errors) {
      return new Rejected(errors);
    }

    static ResetConfigurationResult commandNotApplied(
        UUID commandId, CommandAggregateStatus status) {
      return new CommandNotApplied(commandId, status);
    }
  }

  public sealed interface ClearDemoDataResult {
    record Success() implements ClearDemoDataResult {}

    record NotConfigured() implements ClearDemoDataResult {}

    record Failed(String message) implements ClearDemoDataResult {}
  }

  public record ResetAllConfigurationResult(List<ServiceResetOutcome> outcomes) {

    public record ServiceResetOutcome(
        String applicationName, ResetConfigurationResult result) {}

    public int successCount() {
      return (int)
          outcomes.stream().filter(outcome -> outcome.result() instanceof ResetConfigurationResult.Success).count();
    }

    public boolean allSucceeded() {
      return !outcomes.isEmpty() && successCount() == outcomes.size();
    }

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
                                  outcome.applicationName() + "." + error.field(), error.message()));
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
