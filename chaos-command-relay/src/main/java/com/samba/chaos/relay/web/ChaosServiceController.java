package com.samba.chaos.relay.web;

import com.samba.chaos.relay.console.ChaosConfigurationResetService;
import com.samba.chaos.relay.console.ChaosConfigurationResetService.ResetConfigurationResult;
import com.samba.chaos.relay.console.ChaosMonkeyRuntimeSnapshot;
import com.samba.chaos.relay.model.ChaosClearDemoDataResponse;
import com.samba.chaos.relay.model.ChaosCommandRequest;
import com.samba.chaos.relay.model.ChaosCommandStatusResponse;
import com.samba.chaos.relay.model.ChaosConfigurationResetAllResponse;
import com.samba.chaos.relay.model.ChaosConfigurationResetResponse;
import com.samba.chaos.relay.model.ChaosMaintenanceRequest;
import com.samba.chaos.relay.model.ChaosServiceStatusResponse;
import com.samba.chaos.relay.model.ChaosServiceStatusSummary;
import com.samba.chaos.relay.model.CommandAggregateStatus;
import com.samba.chaos.relay.model.ValidationErrorResponse;
import com.samba.chaos.relay.service.ChaosCommandService;
import com.samba.chaos.relay.service.ChaosCommandValidator;
import com.samba.chaos.relay.service.ChaosServiceMaintenanceService;
import com.samba.chaos.relay.service.ChaosServiceStatusService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Per-service status, maintenance, and reset.
 *
 * <pre>
 * GET  /internal/v1/chaos/services
 * GET  /internal/v1/chaos/services/{applicationName}
 * GET  /internal/v1/chaos/services/{applicationName}/history
 * GET  /internal/v1/chaos/services/{applicationName}/actuator
 * POST /internal/v1/chaos/services/{applicationName}/enable
 * POST /internal/v1/chaos/services/{applicationName}/disable
 * POST /internal/v1/chaos/services/{applicationName}/reset
 * POST /internal/v1/chaos/services/{applicationName}/clear-demo-data
 * POST /internal/v1/chaos/services/{applicationName}/commands
 * POST /internal/v1/chaos/services/reset-all
 * </pre>
 *
 * <p>An unknown allowlist name is 404. Enable and disable return 202. Reset waits for a terminal
 * aggregate: 200 when {@code APPLIED}, 409 otherwise. Reset-all always returns 200 with one outcome
 * per allowlisted service. Clear-demo-data does not change the Chaos Monkey assault.
 */
@RestController
@RequestMapping("/internal/v1/chaos/services")
public class ChaosServiceController {

  private final ChaosServiceStatusService statusService;
  private final ChaosServiceMaintenanceService maintenanceService;
  private final ChaosConfigurationResetService configurationResetService;
  private final ChaosCommandService commandService;
  private final ChaosCommandValidator validator;

  /**
   * Creates the controller.
   *
   * @param statusService allowlisted service reads
   * @param maintenanceService enable and disable
   * @param configurationResetService reset that waits for {@code APPLIED}
   * @param commandService command submit for one service path
   * @param validator maps field errors onto the error body
   */
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

  /**
   * Lists every allowlisted target with its latest aggregate.
   *
   * @return one summary per configured target, in allowlist order
   */
  @GetMapping
  public List<ChaosServiceStatusSummary> listServices() {
    return statusService.listServices();
  }

  /**
   * Returns relay state and the last command for one target.
   *
   * @param applicationName Eureka application name, for example {@code chaos-poc-demo}
   * @return 200 when the name is allowlisted, otherwise 404
   */
  @GetMapping("/{applicationName}")
  public ResponseEntity<ChaosServiceStatusResponse> getService(
      @PathVariable String applicationName) {
    return statusService
        .getService(applicationName)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  /**
   * Returns commands for one target, newest first.
   *
   * <p>Each item includes {@code issuedBy}, {@code expiresAt}, and {@code assault} so a client can
   * render the Chaos Monkey YAML for that patch.
   *
   * @param applicationName allowlisted Eureka application name
   * @return 200 with the history, or 404 when the name is not allowlisted
   */
  @GetMapping("/{applicationName}/history")
  public ResponseEntity<List<ChaosCommandStatusResponse>> getServiceHistory(
      @PathVariable String applicationName) {
    return statusService
        .getHistory(applicationName)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  /**
   * Reads {@code /actuator/chaosmonkey/status} and {@code /assaults} on each UP instance.
   *
   * <pre>
   * GET /internal/v1/chaos/services/chaos-poc-demo/actuator
   * 200 { "configured": true, "reachable": true, "enabled": false }
   * </pre>
   *
   * @param applicationName allowlisted Eureka application name
   * @return 200 with the snapshot, or 404 when the name is not allowlisted
   */
  @GetMapping("/{applicationName}/actuator")
  public ResponseEntity<ChaosMonkeyRuntimeSnapshot> actuator(@PathVariable String applicationName) {
    return statusService
        .probe(applicationName)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  /**
   * Re-enables the last applied configure assault. {@code expiresAt} is required.
   *
   * @param applicationName allowlisted Eureka application name
   * @param request issuer and lease
   * @return 202 when published, 400 when no prior assault exists or the body is invalid
   */
  @PostMapping("/{applicationName}/enable")
  public ResponseEntity<?> enable(
      @PathVariable String applicationName, @Valid @RequestBody ChaosMaintenanceRequest request) {
    return toMaintenanceResponse(maintenanceService.enable(applicationName, request));
  }

  /**
   * Publishes {@code DISABLE} and returns immediately. It does not wait for {@code APPLIED}.
   *
   * @param applicationName allowlisted Eureka application name
   * @param request issuer; {@code expiresAt} is ignored
   * @return 202 when published, 400 when validation rejects the command
   */
  @PostMapping("/{applicationName}/disable")
  public ResponseEntity<?> disable(
      @PathVariable String applicationName, @Valid @RequestBody ChaosMaintenanceRequest request) {
    return toMaintenanceResponse(maintenanceService.disable(applicationName, request));
  }

  /**
   * Publishes {@code DISABLE} and blocks until the aggregate is terminal.
   *
   * <pre>
   * POST /internal/v1/chaos/services/chaos-poc-demo/reset
   * { "issuedBy": "chaos-console" }
   *
   * 200 { "commandId": "...", "commandStatus": "APPLIED" }
   * </pre>
   *
   * @param applicationName allowlisted Eureka application name
   * @param request issuer; a blank {@code issuedBy} becomes {@code chaos-console}
   * @return 200 when APPLIED, 400 when rejected, 409 when FAILED or TIMED_OUT
   */
  @PostMapping("/{applicationName}/reset")
  public ResponseEntity<?> reset(
      @PathVariable String applicationName, @Valid @RequestBody ChaosMaintenanceRequest request) {
    return toResetResponse(configurationResetService.resetConfiguration(applicationName, request));
  }

  /**
   * Calls {@code POST /api/v1/admin/reset} on each UP instance. The assault is left unchanged.
   *
   * @param applicationName allowlisted Eureka application name
   * @return 200 when every instance accepted the reset, 404 when none are UP, 502 when a call fails
   */
  @PostMapping("/{applicationName}/clear-demo-data")
  public ResponseEntity<?> clearDemoData(@PathVariable String applicationName) {
    return switch (configurationResetService.clearDemoData(applicationName)) {
      case ChaosConfigurationResetService.ClearDemoDataResult.Success success ->
          ResponseEntity.ok(new ChaosClearDemoDataResponse(true, null));
      case ChaosConfigurationResetService.ClearDemoDataResult.NotConfigured notConfigured ->
          ResponseEntity.status(HttpStatus.NOT_FOUND)
              .body(
                  new ChaosClearDemoDataResponse(
                      false, "No admin reset URL configured for " + applicationName));
      case ChaosConfigurationResetService.ClearDemoDataResult.Failed failed ->
          ResponseEntity.status(HttpStatus.BAD_GATEWAY)
              .body(new ChaosClearDemoDataResponse(false, failed.message()));
    };
  }

  /**
   * Resets each allowlisted service in order. The HTTP status stays 200 when some services fail.
   *
   * @param request issuer applied to every service
   * @return one outcome per allowlisted application
   */
  @PostMapping("/reset-all")
  public ResponseEntity<?> resetAll(@Valid @RequestBody ChaosMaintenanceRequest request) {
    ChaosConfigurationResetService.ResetAllConfigurationResult result =
        configurationResetService.resetAllConfigurations(request);
    return ResponseEntity.ok(toResetAllResponse(result));
  }

  /**
   * Submits a command whose {@code targetApplication} must equal the path.
   *
   * @param applicationName path target
   * @param request command body
   * @return the same statuses as {@code POST /internal/v1/chaos/commands}, or 400 on a path
   *     mismatch
   */
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
    return switch (result) {
      case ChaosServiceMaintenanceService.MaintenanceResult.Accepted accepted ->
          ResponseEntity.status(HttpStatus.ACCEPTED).body(accepted.response());
      case ChaosServiceMaintenanceService.MaintenanceResult.Rejected rejected ->
          ResponseEntity.badRequest().body(validator.toErrorResponse(rejected.errors()));
    };
  }

  private ResponseEntity<?> toResetResponse(
      ChaosConfigurationResetService.ResetConfigurationResult result) {
    return switch (result) {
      case ChaosConfigurationResetService.ResetConfigurationResult.Success success ->
          ResponseEntity.ok(
              new ChaosConfigurationResetResponse(
                  success.commandId(), CommandAggregateStatus.APPLIED));
      case ChaosConfigurationResetService.ResetConfigurationResult.Rejected rejected ->
          ResponseEntity.badRequest().body(validator.toErrorResponse(rejected.errors()));
      case ChaosConfigurationResetService.ResetConfigurationResult.CommandNotApplied failed ->
          ResponseEntity.status(HttpStatus.CONFLICT)
              .body(new ChaosConfigurationResetResponse(failed.commandId(), failed.status()));
    };
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
    return switch (resetResult) {
      case ResetConfigurationResult.Success success ->
          new ChaosConfigurationResetAllResponse.ServiceResetOutcome(
              outcome.applicationName(),
              success.commandId(),
              CommandAggregateStatus.APPLIED,
              List.of());
      case ResetConfigurationResult.Rejected rejected ->
          new ChaosConfigurationResetAllResponse.ServiceResetOutcome(
              outcome.applicationName(), null, CommandAggregateStatus.FAILED, rejected.errors());
      case ResetConfigurationResult.CommandNotApplied failed ->
          new ChaosConfigurationResetAllResponse.ServiceResetOutcome(
              outcome.applicationName(),
              failed.commandId(),
              failed.status(),
              List.of(
                  new ValidationErrorResponse.FieldError(
                      "command", "disable command finished with status " + failed.status())));
    };
  }

  private ResponseEntity<?> toCommandResponse(ChaosCommandService.SubmitResult result) {
    return switch (result) {
      case ChaosCommandService.SubmitResult.Accepted accepted ->
          ResponseEntity.status(HttpStatus.ACCEPTED).body(accepted.response());
      case ChaosCommandService.SubmitResult.Rejected rejected ->
          ResponseEntity.badRequest().body(rejected.response());
      case ChaosCommandService.SubmitResult.Unavailable unavailable ->
          ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(unavailable.response());
    };
  }
}
