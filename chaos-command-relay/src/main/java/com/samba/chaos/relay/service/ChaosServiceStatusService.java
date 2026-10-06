package com.samba.chaos.relay.service;

import com.samba.chaos.command.ChaosAssaultConfig;
import com.samba.chaos.command.ChaosCommandAction;
import com.samba.chaos.relay.config.ChaosRelayProperties;
import com.samba.chaos.relay.console.ChaosMonkeyActuatorProbe;
import com.samba.chaos.relay.console.ChaosMonkeyRuntimeSnapshot;
import com.samba.chaos.relay.model.ChaosCommandStatusResponse;
import com.samba.chaos.relay.model.ChaosServiceStatusResponse;
import com.samba.chaos.relay.model.ChaosServiceStatusSummary;
import com.samba.chaos.relay.model.CommandAggregateStatus;
import com.samba.chaos.relay.model.ServiceConfigState;
import com.samba.chaos.relay.store.ChaosCommandStore;
import com.samba.chaos.relay.store.CommandRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Allowlisted service reads for the operator console.
 *
 * <p>Status and history come from the in-memory store. The actuator snapshot is a live read of each
 * UP instance and is not stored.
 */
@Service
public class ChaosServiceStatusService {

  private final ChaosCommandStore commandStore;
  private final ChaosRelayProperties properties;
  private final ChaosCommandStatusService statusService;
  private final TargetInstancesResolver instancesResolver;
  private final ChaosMonkeyActuatorProbe actuatorProbe;

  /**
   * Creates the status service.
   *
   * @param properties allowlist and verify-UI URL
   * @param instancesResolver current UP instances
   * @param commandStore command history
   * @param statusService aggregate for the latest command
   * @param actuatorProbe live Chaos Monkey actuator read
   */
  public ChaosServiceStatusService(
      ChaosCommandStore commandStore,
      ChaosRelayProperties properties,
      ChaosCommandStatusService statusService,
      TargetInstancesResolver instancesResolver,
      ChaosMonkeyActuatorProbe actuatorProbe) {
    this.commandStore = commandStore;
    this.properties = properties;
    this.statusService = statusService;
    this.instancesResolver = instancesResolver;
    this.actuatorProbe = actuatorProbe;
  }

  /**
   * One summary per allowlisted application, in configured order.
   *
   * @return summaries; an app with no commands reports {@code NOT_CONFIGURED}
   */
  public List<ChaosServiceStatusSummary> listServices() {
    return properties.getAllowedTargetApplications().stream().map(this::toSummary).toList();
  }

  /**
   * Relay view of one target: instance count, latest command, and verify URL.
   *
   * @param applicationName Eureka application name
   * @return empty when the name is not allowlisted
   */
  public Optional<ChaosServiceStatusResponse> getService(String applicationName) {
    if (!properties.getAllowedTargetApplications().contains(applicationName)) {
      return Optional.empty();
    }
    return Optional.of(toResponse(applicationName));
  }

  /** Live Chaos Monkey snapshot for an allowlisted service. */
  public Optional<ChaosMonkeyRuntimeSnapshot> probe(String applicationName) {
    if (!properties.getAllowedTargetApplications().contains(applicationName)) {
      return Optional.empty();
    }
    return Optional.of(actuatorProbe.probe(applicationName));
  }

  /**
   * Commands for one target, newest {@code publishedAt} first.
   *
   * @param applicationName Eureka application name
   * @return empty when the name is not allowlisted; an empty list when it has no commands
   */
  public Optional<List<ChaosCommandStatusResponse>> getHistory(String applicationName) {
    if (!properties.getAllowedTargetApplications().contains(applicationName)) {
      return Optional.empty();
    }
    return Optional.of(
        commandStore.findByApplication(applicationName).stream()
            .map(statusService::toStatusResponse)
            .toList());
  }

  private ChaosServiceStatusSummary toSummary(String applicationName) {
    ChaosServiceStatusResponse response = toResponse(applicationName);
    return new ChaosServiceStatusSummary(
        response.applicationName(),
        response.registryEnabled(),
        response.configState(),
        response.cmEnabled(),
        response.lastCommandId(),
        response.lastCommandStatus(),
        response.eurekaUpCount(),
        response.expectedInstances());
  }

  private ChaosServiceStatusResponse toResponse(String applicationName) {
    Optional<CommandRecord> latest = commandStore.findLatestByApplication(applicationName);
    int liveInstances = instancesResolver.resolveUp(applicationName).size();

    if (latest.isEmpty()) {
      return new ChaosServiceStatusResponse(
          applicationName,
          true,
          ServiceConfigState.DEFAULT,
          resolveCmEnabled(applicationName, null, null),
          null,
          null,
          null,
          null,
          null,
          null,
          liveInstances,
          liveInstances,
          null,
          null);
    }

    CommandRecord record = latest.get();
    CommandAggregateStatus commandStatus = statusService.aggregateStatus(record);
    ServiceConfigState configState = toConfigState(record, commandStatus);
    Boolean cmEnabled = resolveCmEnabled(applicationName, record, commandStatus);
    ChaosAssaultConfig appliedAssault =
        commandStatus == CommandAggregateStatus.APPLIED ? record.assault() : null;

    return new ChaosServiceStatusResponse(
        applicationName,
        true,
        configState,
        cmEnabled,
        record.commandId(),
        commandStatus,
        record.action(),
        record.publishedAt(),
        record.issuedBy(),
        record.expiresAt(),
        liveInstances,
        record.expectedInstances(),
        record.assault(),
        appliedAssault);
  }

  private Boolean resolveCmEnabled(
      String applicationName, CommandRecord record, CommandAggregateStatus commandStatus) {
    ChaosMonkeyRuntimeSnapshot snapshot = actuatorProbe.probe(applicationName);
    if (snapshot.reachable() && snapshot.enabled() != null) {
      return snapshot.enabled();
    }
    if (record == null || commandStatus == null) {
      return false;
    }
    return toCmEnabledFromMemory(record, commandStatus);
  }

  private ServiceConfigState toConfigState(
      CommandRecord record, CommandAggregateStatus commandStatus) {
    return switch (commandStatus) {
      case APPLIED ->
          record.action() == ChaosCommandAction.DISABLE
              ? ServiceConfigState.DEFAULT
              : ServiceConfigState.APPLIED;
      case FAILED -> ServiceConfigState.FAILED;
      case PENDING, PARTIAL -> ServiceConfigState.DESIRED;
      case TIMED_OUT -> ServiceConfigState.PARTIAL;
      default -> ServiceConfigState.DEFAULT;
    };
  }

  private Boolean toCmEnabledFromMemory(
      CommandRecord record, CommandAggregateStatus commandStatus) {
    if (commandStatus != CommandAggregateStatus.APPLIED) {
      return false;
    }
    return record.action() == ChaosCommandAction.ENABLE
        || record.action() == ChaosCommandAction.CONFIGURE_AND_ENABLE;
  }
}
