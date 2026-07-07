package com.samba.chaos.relay;

import com.samba.chaos.relay.model.ChaosMaintenanceRequest;
import com.samba.chaos.relay.model.ChaosServiceStatusResponse;
import com.samba.chaos.relay.model.ChaosServiceStatusSummary;
import com.samba.chaos.relay.model.CommandAggregateStatus;
import com.samba.chaos.relay.model.ValidationErrorResponse;
import com.samba.chaos.relay.console.ChaosConfigurationResetService;
import com.samba.chaos.relay.console.ChaosConfigurationResetService.ResetConfigurationResult;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/chaos/services")
public class ChaosServiceController {

  private final ChaosServiceStatusService statusService;
  private final ChaosServiceMaintenanceService maintenanceService;
  private final ChaosConfigurationResetService configurationResetService;
  private final ChaosCommandService commandService;
  private final ChaosCommandValidator validator;

  public ChaosServiceController(
      ChaosServiceStatusService statusService,
      ChaosServiceMaintenanceService maintenanceService,
      ChaosConfigurationResetService configurationResetService,
      ChaosCommandService commandService,
      ChaosCommandValidator validator) {
    this.statusService = statusService;
    this.maintenanceService = maintenanceService;
    this.configurationResetService = configurationResetService;
    this.commandService = commandService;
    this.validator = validator;
  }

  @GetMapping
  public List<ChaosServiceStatusSummary> listServices() {
    return statusService.listServices();
  }

  @GetMapping("/{applicationName}")
  public ResponseEntity<ChaosServiceStatusResponse> getService(
      @PathVariable String applicationName) {
    return statusService
        .getService(applicationName)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PostMapping("/{applicationName}/enable")
  public ResponseEntity<?> enable(
      @PathVariable String applicationName,
      @Valid @RequestBody ChaosMaintenanceRequest request) {
    return toMaintenanceResponse(maintenanceService.enable(applicationName, request));
  }

  @PostMapping("/{applicationName}/disable")
  public ResponseEntity<?> disable(
      @PathVariable String applicationName,
      @Valid @RequestBody ChaosMaintenanceRequest request) {
    return toMaintenanceResponse(maintenanceService.disable(applicationName, request));
  }

  @PostMapping("/{applicationName}/reset")
  public ResponseEntity<?> reset(
      @PathVariable String applicationName,
      @Valid @RequestBody ChaosMaintenanceRequest request) {
    return toResetResponse(configurationResetService.resetConfiguration(applicationName, request));
  }

  @PostMapping("/{applicationName}/clear-demo-data")
  public ResponseEntity<?> clearDemoData(@PathVariable String applicationName) {
    ChaosConfigurationResetService.ClearDemoDataResult result =
        configurationResetService.clearDemoData(applicationName);
    if (result instanceof ChaosConfigurationResetService.ClearDemoDataResult.Success) {
      return ResponseEntity.ok(new ChaosClearDemoDataResponse(true, null));
    }
    if (result instanceof ChaosConfigurationResetService.ClearDemoDataResult.NotConfigured) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(
              new ChaosClearDemoDataResponse(
                  false, "No admin reset URL configured for " + applicationName));
    }
    if (result instanceof ChaosConfigurationResetService.ClearDemoDataResult.Failed failed) {
      return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
          .body(new ChaosClearDemoDataResponse(false, failed.message()));
    }
    throw new IllegalStateException("Unknown clear demo data result: " + result);
  }

  @PostMapping("/reset-all")
  public ResponseEntity<?> resetAll(@Valid @RequestBody ChaosMaintenanceRequest request) {
    ChaosConfigurationResetService.ResetAllConfigurationResult result =
        configurationResetService.resetAllConfigurations(request);
    return ResponseEntity.ok(toResetAllResponse(result));
  }

  @PostMapping("/{applicationName}/commands")
  public ResponseEntity<?> submitForService(
      @PathVariable String applicationName, @Valid @RequestBody ChaosCommandRequest request) {
    if (!applicationName.equals(request.targetApplication())) {
      return ResponseEntity.badRequest()
          .body(
              new ValidationErrorResponse(
                  "REJECTED",
                  List.of(
                      new ValidationErrorResponse.FieldError(
                          "targetApplication", "must match path applicationName"))));
    }
    return toCommandResponse(commandService.submit(request));
  }

  private ResponseEntity<?> toMaintenanceResponse(
      ChaosServiceMaintenanceService.MaintenanceResult result) {
    if (result instanceof ChaosServiceMaintenanceService.MaintenanceResult.Accepted accepted) {
      return ResponseEntity.status(HttpStatus.ACCEPTED).body(accepted.response());
    }
    if (result instanceof ChaosServiceMaintenanceService.MaintenanceResult.Rejected rejected) {
      return ResponseEntity.badRequest().body(validator.toErrorResponse(rejected.errors()));
    }
    throw new IllegalStateException("Unknown maintenance result: " + result);
  }

  private ResponseEntity<?> toResetResponse(
      ChaosConfigurationResetService.ResetConfigurationResult result) {
    if (result instanceof ChaosConfigurationResetService.ResetConfigurationResult.Success success) {
      return ResponseEntity.ok(
          new ChaosConfigurationResetResponse(
              success.commandId(), CommandAggregateStatus.APPLIED));
    }
    if (result instanceof ChaosConfigurationResetService.ResetConfigurationResult.Rejected rejected) {
      return ResponseEntity.badRequest().body(validator.toErrorResponse(rejected.errors()));
    }
    if (result
        instanceof ChaosConfigurationResetService.ResetConfigurationResult.CommandNotApplied failed) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(
              new ChaosConfigurationResetResponse(
                  failed.commandId(), failed.status()));
    }
    throw new IllegalStateException("Unknown reset result: " + result);
  }

  private ChaosConfigurationResetAllResponse toResetAllResponse(
      ChaosConfigurationResetService.ResetAllConfigurationResult result) {
    List<ChaosConfigurationResetAllResponse.ServiceResetOutcome> services =
        result.outcomes().stream().map(this::toServiceResetOutcome).toList();
    return new ChaosConfigurationResetAllResponse(
        result.successCount(), result.outcomes().size(), services);
  }

  private ChaosConfigurationResetAllResponse.ServiceResetOutcome toServiceResetOutcome(
      ChaosConfigurationResetService.ResetAllConfigurationResult.ServiceResetOutcome outcome) {
    ResetConfigurationResult resetResult = outcome.result();
    if (resetResult instanceof ResetConfigurationResult.Success success) {
      return new ChaosConfigurationResetAllResponse.ServiceResetOutcome(
          outcome.applicationName(),
          success.commandId(),
          CommandAggregateStatus.APPLIED,
          List.of());
    }
    if (resetResult instanceof ResetConfigurationResult.Rejected rejected) {
      return new ChaosConfigurationResetAllResponse.ServiceResetOutcome(
          outcome.applicationName(), null, CommandAggregateStatus.FAILED, rejected.errors());
    }
    if (resetResult instanceof ResetConfigurationResult.CommandNotApplied failed) {
      return new ChaosConfigurationResetAllResponse.ServiceResetOutcome(
          outcome.applicationName(),
          failed.commandId(),
          failed.status(),
          List.of(
              new ValidationErrorResponse.FieldError(
                  "command",
                  "disable command finished with status " + failed.status())));
    }
    throw new IllegalStateException("Unknown reset result: " + resetResult);
  }

  private ResponseEntity<?> toCommandResponse(ChaosCommandService.SubmitResult result) {
    if (result instanceof ChaosCommandService.SubmitResult.Accepted accepted) {
      return ResponseEntity.status(HttpStatus.ACCEPTED).body(accepted.response());
    }
    if (result instanceof ChaosCommandService.SubmitResult.Rejected rejected) {
      return ResponseEntity.badRequest().body(rejected.response());
    }
    throw new IllegalStateException("Unknown submit result: " + result);
  }
}
