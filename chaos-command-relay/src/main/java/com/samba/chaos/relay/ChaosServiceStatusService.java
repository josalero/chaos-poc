package com.samba.chaos.relay;

import com.samba.chaos.listener.message.ChaosAssaultConfig;
import com.samba.chaos.listener.message.ChaosCommandAction;
import com.samba.chaos.relay.model.ChaosServiceStatusResponse;
import com.samba.chaos.relay.model.ChaosServiceStatusSummary;
import com.samba.chaos.relay.model.CommandAggregateStatus;
import com.samba.chaos.relay.model.ServiceConfigState;
import com.samba.chaos.relay.InMemoryChaosCommandStore.CommandRecord;
import com.samba.chaos.relay.console.ChaosMonkeyActuatorProbe;
import com.samba.chaos.relay.console.ChaosMonkeyRuntimeSnapshot;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ChaosServiceStatusService {

  private final ChaosCommandStore commandStore;
  private final ChaosRelayProperties properties;
  private final ChaosCommandStatusService statusService;
  private final ExpectedInstancesResolver expectedInstancesResolver;
  private final ChaosMonkeyActuatorProbe actuatorProbe;

  public ChaosServiceStatusService(
      ChaosCommandStore commandStore,
      ChaosRelayProperties properties,
      ChaosCommandStatusService statusService,
      ExpectedInstancesResolver expectedInstancesResolver,
      ChaosMonkeyActuatorProbe actuatorProbe) {
    this.commandStore = commandStore;
    this.properties = properties;
    this.statusService = statusService;
    this.expectedInstancesResolver = expectedInstancesResolver;
    this.actuatorProbe = actuatorProbe;
  }

  public List<ChaosServiceStatusSummary> listServices() {
    return properties.getAllowedTargetApplications().stream()
        .map(this::toSummary)
        .toList();
  }

  public Optional<ChaosServiceStatusResponse> getService(String applicationName) {
    if (!properties.getAllowedTargetApplications().contains(applicationName)) {
      return Optional.empty();
    }
    return Optional.of(toResponse(applicationName));
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
    int expectedInstances = expectedInstancesResolver.resolve(applicationName);

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
          expectedInstances,
          expectedInstances,
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
        expectedInstances,
        expectedInstances,
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
