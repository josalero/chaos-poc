package com.samba.chaos.relay;

import com.samba.chaos.listener.message.ChaosAssaultConfig;
import com.samba.chaos.listener.message.ChaosCommandAction;
import com.samba.chaos.relay.model.ChaosCommandSubmitResponse;
import com.samba.chaos.relay.model.ChaosMaintenanceRequest;
import com.samba.chaos.relay.model.CommandAggregateStatus;
import com.samba.chaos.relay.model.ValidationErrorResponse;
import com.samba.chaos.relay.model.ValidationErrorResponse.FieldError;
import com.samba.chaos.relay.InMemoryChaosCommandStore.CommandRecord;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ChaosServiceMaintenanceService {

  private final ChaosCommandService commandService;
  private final ChaosCommandStore commandStore;
  private final ChaosCommandStatusService statusService;

  public ChaosServiceMaintenanceService(
      ChaosCommandService commandService,
      ChaosCommandStore commandStore,
      ChaosCommandStatusService statusService) {
    this.commandService = commandService;
    this.commandStore = commandStore;
    this.statusService = statusService;
  }

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

    return submit(
        applicationName,
        ChaosCommandAction.ENABLE,
        assault.get(),
        request);
  }

  public MaintenanceResult disable(String applicationName, ChaosMaintenanceRequest request) {
    return submit(applicationName, ChaosCommandAction.DISABLE, null, request);
  }

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

  public sealed interface MaintenanceResult {
    record Accepted(ChaosCommandSubmitResponse response) implements MaintenanceResult {}

    record Rejected(List<FieldError> errors) implements MaintenanceResult {}

    static MaintenanceResult accepted(ChaosCommandSubmitResponse response) {
      return new Accepted(response);
    }

    static MaintenanceResult rejected(List<FieldError> errors) {
      return new Rejected(errors);
    }

    static MaintenanceResult rejected(ValidationErrorResponse response) {
      return new Rejected(response.errors());
    }
  }
}
