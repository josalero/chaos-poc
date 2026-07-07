package com.samba.chaos.relay.console;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samba.chaos.listener.message.ChaosAssaultConfig;
import com.samba.chaos.relay.model.ChaosCommandStatusResponse;
import com.samba.chaos.relay.model.ChaosServiceStatusResponse;
import com.samba.chaos.relay.model.ChaosServiceStatusSummary;
import com.samba.chaos.relay.ChaosCommandService;
import com.samba.chaos.relay.ChaosCommandStatusService;
import com.samba.chaos.relay.ChaosCommandStore;
import com.samba.chaos.relay.ChaosServiceMaintenanceService;
import com.samba.chaos.relay.ChaosServiceStatusService;
import com.samba.chaos.relay.InMemoryChaosCommandStore.CommandRecord;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ChaosConsoleService {

  private final ChaosServiceStatusService statusService;
  private final ChaosCommandService commandService;
  private final ChaosServiceMaintenanceService maintenanceService;
  private final ChaosPresetService presetService;
  private final ChaosCommandStore commandStore;
  private final ChaosCommandStatusService commandStatusService;
  private final ChaosMonkeyActuatorProbe actuatorProbe;
  private final ChaosConfigurationResetService configurationResetService;
  private final ObjectMapper objectMapper;

  public ChaosConsoleService(
      ChaosServiceStatusService statusService,
      ChaosCommandService commandService,
      ChaosServiceMaintenanceService maintenanceService,
      ChaosPresetService presetService,
      ChaosCommandStore commandStore,
      ChaosCommandStatusService commandStatusService,
      ChaosMonkeyActuatorProbe actuatorProbe,
      ChaosConfigurationResetService configurationResetService,
      ObjectMapper objectMapper) {
    this.statusService = statusService;
    this.commandService = commandService;
    this.maintenanceService = maintenanceService;
    this.presetService = presetService;
    this.commandStore = commandStore;
    this.commandStatusService = commandStatusService;
    this.actuatorProbe = actuatorProbe;
    this.configurationResetService = configurationResetService;
    this.objectMapper = objectMapper;
  }

  public List<ChaosServiceStatusSummary> listServices() {
    return statusService.listServices();
  }

  public Optional<ChaosServiceStatusResponse> getService(String applicationName) {
    return statusService.getService(applicationName);
  }

  public Optional<ChaosCommandStatusResponse> getCommandStatus(UUID commandId) {
    return commandService.getStatus(commandId);
  }

  public List<ChaosPreset> listPresets() {
    return presetService.listPresets();
  }

  public List<ChaosCommandHistoryEntry> listCommandHistory(String applicationName) {
    return commandStore.findByApplication(applicationName).stream()
        .map(this::toHistoryEntry)
        .toList();
  }

  public ChaosMonkeyRuntimeSnapshot probeChaosMonkey(String applicationName) {
    return actuatorProbe.probe(applicationName);
  }

  public Optional<String> presetJson(String presetId) {
    return presetService.findById(presetId).map(ChaosPreset::json);
  }

  public ChaosAssaultConfig parseAssault(String assaultJson) throws IOException {
    if (assaultJson == null || assaultJson.isBlank()) {
      return null;
    }
    JsonNode node = objectMapper.readTree(assaultJson);
    return objectMapper.treeToValue(node, ChaosAssaultConfig.class);
  }

  public ChaosAssaultConfig assaultFromPreset(String presetId) throws IOException {
    if (presetId == null || presetId.isBlank()) {
      return null;
    }
    return presetService
        .presetAssault(presetId)
        .map(
            node -> {
              try {
                return objectMapper.treeToValue(node, ChaosAssaultConfig.class);
              } catch (IOException ex) {
                throw new IllegalStateException("Invalid preset assault: " + presetId, ex);
              }
            })
        .orElse(null);
  }

  public ChaosCommandService.SubmitResult submitCommand(ChaosCommandForm form)
      throws IOException {
    ChaosAssaultConfig assault =
        form.getAssaultJson() != null && !form.getAssaultJson().isBlank()
            ? parseAssault(form.getAssaultJson())
            : assaultFromPreset(form.getPresetId());
    return commandService.submit(form.toRequest(assault));
  }

  public ChaosServiceMaintenanceService.MaintenanceResult enable(
      String applicationName, ChaosMaintenanceForm form) {
    return maintenanceService.enable(applicationName, form.toRequest());
  }

  public ChaosServiceMaintenanceService.MaintenanceResult disable(
      String applicationName, ChaosMaintenanceForm form) {
    return maintenanceService.disable(applicationName, form.toRequest());
  }

  public ChaosConfigurationResetService.ResetConfigurationResult resetConfiguration(
      String applicationName, ChaosMaintenanceForm form) {
    return configurationResetService.resetConfiguration(applicationName, form.toRequest());
  }

  public ChaosConfigurationResetService.ResetAllConfigurationResult resetAllConfigurations(
      ChaosMaintenanceForm form) {
    return configurationResetService.resetAllConfigurations(form.toRequest());
  }

  public ChaosConfigurationResetService.ClearDemoDataResult clearDemoData(String applicationName) {
    return configurationResetService.clearDemoData(applicationName);
  }

  private ChaosCommandHistoryEntry toHistoryEntry(CommandRecord record) {
    return new ChaosCommandHistoryEntry(
        record.commandId(),
        record.action(),
        commandStatusService.aggregateStatus(record),
        record.publishedAt(),
        record.issuedBy(),
        record.expiresAt(),
        record.successCount(),
        record.failureCount());
  }
}
