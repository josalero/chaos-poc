package com.samba.chaos.relay.console;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samba.chaos.listener.message.ChaosAssaultConfig;
import com.samba.chaos.listener.message.ChaosCommandAction;
import com.samba.chaos.relay.model.ChaosCommandSubmitResponse;
import com.samba.chaos.relay.model.ChaosServiceStatusResponse;
import com.samba.chaos.relay.model.ValidationErrorResponse.FieldError;
import com.samba.chaos.relay.ChaosCommandService;
import com.samba.chaos.relay.ChaosRelayProperties;
import com.samba.chaos.relay.ChaosServiceMaintenanceService;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/chaos")
public class ChaosConsoleController {

  private static final Set<String> SERVICE_TABS =
      Set.of("overview", "actuator", "commands", "maintenance");

  private final ChaosConsoleService consoleService;
  private final ChaosRelayProperties relayProperties;
  private final ObjectMapper objectMapper;

  public ChaosConsoleController(
      ChaosConsoleService consoleService,
      ChaosRelayProperties relayProperties,
      ObjectMapper objectMapper) {
    this.consoleService = consoleService;
    this.relayProperties = relayProperties;
    this.objectMapper = objectMapper;
  }

  @ModelAttribute("defaultTargetApplication")
  public String defaultTargetApplication() {
    List<String> targets = relayProperties.getAllowedTargetApplications();
    return targets.isEmpty() ? null : targets.get(0);
  }

  @ModelAttribute("targetApplications")
  public List<String> targetApplications() {
    return relayProperties.getAllowedTargetApplications();
  }

  @ModelAttribute("verifyUiUrl")
  public String verifyUiUrl() {
    return relayProperties.getVerifyUiUrl();
  }

  @GetMapping
  public String dashboard(Model model) {
    model.addAttribute("services", consoleService.listServices());
    return "chaos/dashboard";
  }

  @GetMapping("/services/{applicationName}")
  public String serviceDetail(
      @PathVariable String applicationName,
      @RequestParam(required = false, defaultValue = "overview") String tab,
      Model model) {
    ChaosServiceStatusResponse status =
        consoleService
            .getService(applicationName)
            .orElseThrow(() -> new IllegalArgumentException("Unknown service: " + applicationName));

    ChaosMaintenanceForm maintenanceForm = new ChaosMaintenanceForm();
    maintenanceForm.setExpiresAt(defaultExpiresAt());

    model.addAttribute("status", status);
    model.addAttribute("maintenanceForm", maintenanceForm);
    model.addAttribute("desiredAssaultJson", formatAssault(status.desiredAssault()));
    model.addAttribute("appliedAssaultJson", formatAssault(status.appliedAssault()));
    model.addAttribute("commandHistory", consoleService.listCommandHistory(applicationName));
    model.addAttribute("chaosMonkey", consoleService.probeChaosMonkey(applicationName));
    model.addAttribute("activeTab", resolveServiceTab(tab));
    return "chaos/service-detail";
  }

  @PostMapping("/services/{applicationName}/enable")
  public String enable(
      @PathVariable String applicationName,
      @ModelAttribute ChaosMaintenanceForm form,
      RedirectAttributes redirectAttributes) {
    return handleMaintenance(applicationName, consoleService.enable(applicationName, form), redirectAttributes);
  }

  @PostMapping("/services/{applicationName}/disable")
  public String disable(
      @PathVariable String applicationName,
      @ModelAttribute ChaosMaintenanceForm form,
      RedirectAttributes redirectAttributes) {
    return handleMaintenance(applicationName, consoleService.disable(applicationName, form), redirectAttributes);
  }

  @PostMapping("/services/{applicationName}/reset")
  public String reset(
      @PathVariable String applicationName,
      @ModelAttribute ChaosMaintenanceForm form,
      RedirectAttributes redirectAttributes) {
    return handleResetConfiguration(applicationName, form, redirectAttributes);
  }

  @PostMapping("/services/{applicationName}/clear-demo-data")
  public String clearDemoData(
      @PathVariable String applicationName, RedirectAttributes redirectAttributes) {
    ChaosConfigurationResetService.ClearDemoDataResult result =
        consoleService.clearDemoData(applicationName);

    if (result instanceof ChaosConfigurationResetService.ClearDemoDataResult.Success) {
      redirectAttributes.addFlashAttribute(
          "successMessage", "Demo data cleared for " + applicationName + ".");
    } else if (result instanceof ChaosConfigurationResetService.ClearDemoDataResult.NotConfigured) {
      redirectAttributes.addFlashAttribute(
          "errors",
          List.of(
              new FieldError(
                  "admin",
                  "No admin reset URL configured for "
                      + applicationName
                      + " (chaos.relay.admin-base-urls).")));
    } else if (result instanceof ChaosConfigurationResetService.ClearDemoDataResult.Failed failed) {
      redirectAttributes.addFlashAttribute(
          "errors", List.of(new FieldError("admin", failed.message())));
    }
    return serviceMaintenanceUrl(applicationName);
  }

  private static String resolveServiceTab(String tab) {
    return SERVICE_TABS.contains(tab) ? tab : "overview";
  }

  private static String serviceMaintenanceUrl(String applicationName) {
    return "redirect:/chaos/services/" + applicationName + "?tab=maintenance";
  }

  @PostMapping("/reset-configuration")
  public String resetConfiguration(
      @RequestParam String applicationName,
      @ModelAttribute ChaosMaintenanceForm form,
      RedirectAttributes redirectAttributes) {
    return handleResetConfiguration(applicationName, form, redirectAttributes);
  }

  @PostMapping("/reset-all-configuration")
  public String resetAllConfiguration(
      @ModelAttribute ChaosMaintenanceForm form, RedirectAttributes redirectAttributes) {
    ChaosConfigurationResetService.ResetAllConfigurationResult result =
        consoleService.resetAllConfigurations(form);

    if (result.allSucceeded()) {
      redirectAttributes.addFlashAttribute(
          "successMessage",
          "Chaos Monkey configuration reset for "
              + result.outcomes().size()
              + " services (assaults disabled).");
      return "redirect:/chaos";
    }

    if (result.successCount() > 0) {
      redirectAttributes.addFlashAttribute(
          "successMessage",
          "Partial reset: "
              + result.successCount()
              + " of "
              + result.outcomes().size()
              + " services reset.");
    }
    if (!result.failedServiceErrors().isEmpty()) {
      redirectAttributes.addFlashAttribute("errors", result.failedServiceErrors());
    }
    return "redirect:/chaos";
  }

  @GetMapping("/commands/new")
  public String newCommand(
      Model model, @RequestParam(required = false) String applicationName) {
    ChaosCommandForm form = new ChaosCommandForm();
    if (applicationName != null) {
      form.setTargetApplication(applicationName);
    } else if (!relayProperties.getAllowedTargetApplications().isEmpty()) {
      form.setTargetApplication(relayProperties.getAllowedTargetApplications().get(0));
    }
    form.setExpiresAt(defaultExpiresAt());

    model.addAttribute("form", form);
    model.addAttribute("presets", consoleService.listPresets());
    model.addAttribute("actions", ChaosCommandAction.values());
    model.addAttribute("targets", relayProperties.getAllowedTargetApplications());
    return "chaos/command-form";
  }

  @PostMapping("/commands")
  public String submitCommand(@ModelAttribute ChaosCommandForm form, RedirectAttributes redirectAttributes)
      throws IOException {
    ChaosCommandService.SubmitResult result = consoleService.submitCommand(form);
    if (result instanceof ChaosCommandService.SubmitResult.Accepted accepted) {
      ChaosCommandSubmitResponse response = accepted.response();
      return "redirect:/chaos/commands/" + response.commandId();
    }
    if (result instanceof ChaosCommandService.SubmitResult.Rejected rejected) {
      redirectAttributes.addFlashAttribute("errors", rejected.response().errors());
      return "redirect:/chaos/commands/new";
    }
    throw new IllegalStateException("Unknown submit result: " + result);
  }

  @GetMapping("/commands/{commandId}")
  public String commandStatus(@PathVariable UUID commandId, Model model) {
    model.addAttribute(
        "status",
        consoleService
            .getCommandStatus(commandId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown command: " + commandId)));
    return "chaos/command-status";
  }

  private String handleMaintenance(
      String applicationName,
      ChaosServiceMaintenanceService.MaintenanceResult result,
      RedirectAttributes redirectAttributes) {
    if (result instanceof ChaosServiceMaintenanceService.MaintenanceResult.Accepted accepted) {
      return "redirect:/chaos/commands/" + accepted.response().commandId();
    }
    if (result instanceof ChaosServiceMaintenanceService.MaintenanceResult.Rejected rejected) {
      redirectAttributes.addFlashAttribute("errors", rejected.errors());
      return "redirect:/chaos/services/" + applicationName + "?tab=maintenance";
    }
    throw new IllegalStateException("Unknown maintenance result: " + result);
  }

  private String handleResetConfiguration(
      String applicationName,
      ChaosMaintenanceForm form,
      RedirectAttributes redirectAttributes) {
    ChaosConfigurationResetService.ResetConfigurationResult result =
        consoleService.resetConfiguration(applicationName, form);

    if (result
        instanceof ChaosConfigurationResetService.ResetConfigurationResult.Success success) {
      redirectAttributes.addFlashAttribute(
          "successMessage", "Chaos Monkey configuration reset (assaults disabled).");
      redirectAttributes.addFlashAttribute("lastCommandId", success.commandId());
      return serviceMaintenanceUrl(applicationName);
    }
    if (result
        instanceof ChaosConfigurationResetService.ResetConfigurationResult.Rejected rejected) {
      redirectAttributes.addFlashAttribute("errors", rejected.errors());
      return serviceMaintenanceUrl(applicationName);
    }
    if (result
        instanceof ChaosConfigurationResetService.ResetConfigurationResult.CommandNotApplied failed) {
      redirectAttributes.addFlashAttribute(
          "errors",
          List.of(
              new FieldError(
                  "command",
                  "disable command finished with status "
                      + failed.status()
                      + " — see command "
                      + failed.commandId())));
      redirectAttributes.addFlashAttribute("lastCommandId", failed.commandId());
      return serviceMaintenanceUrl(applicationName);
    }
    throw new IllegalStateException("Unknown reset result: " + result);
  }

  private static String defaultExpiresAt() {
    return Instant.now().plus(2, ChronoUnit.HOURS).toString();
  }

  private String formatAssault(ChaosAssaultConfig assault) {
    if (assault == null) {
      return null;
    }
    try {
      return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(assault);
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to format assault config", ex);
    }
  }
}
