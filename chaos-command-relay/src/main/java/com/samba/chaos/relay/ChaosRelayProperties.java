package com.samba.chaos.relay;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chaos.relay")
public class ChaosRelayProperties {

  private List<String> allowedTargetApplications = List.of();
  private int statusTimeoutSeconds = 30;
  private int commandTtlHours = 24;
  private Map<String, Integer> expectedInstancesFallback = new HashMap<>();
  private Map<String, String> actuatorBaseUrls = new HashMap<>();
  private Map<String, String> adminBaseUrls = new HashMap<>();
  private String verifyUiUrl = "http://localhost:18000";
  private Rabbit rabbit = new Rabbit();

  public List<String> getAllowedTargetApplications() {
    return allowedTargetApplications;
  }

  public void setAllowedTargetApplications(List<String> allowedTargetApplications) {
    this.allowedTargetApplications = allowedTargetApplications;
  }

  public int getStatusTimeoutSeconds() {
    return statusTimeoutSeconds;
  }

  public void setStatusTimeoutSeconds(int statusTimeoutSeconds) {
    this.statusTimeoutSeconds = statusTimeoutSeconds;
  }

  public int getCommandTtlHours() {
    return commandTtlHours;
  }

  public void setCommandTtlHours(int commandTtlHours) {
    this.commandTtlHours = commandTtlHours;
  }

  public Map<String, Integer> getExpectedInstancesFallback() {
    return expectedInstancesFallback;
  }

  public void setExpectedInstancesFallback(Map<String, Integer> expectedInstancesFallback) {
    this.expectedInstancesFallback = expectedInstancesFallback;
  }

  public Map<String, String> getActuatorBaseUrls() {
    return actuatorBaseUrls;
  }

  public void setActuatorBaseUrls(Map<String, String> actuatorBaseUrls) {
    this.actuatorBaseUrls = actuatorBaseUrls;
  }

  public Map<String, String> getAdminBaseUrls() {
    return adminBaseUrls;
  }

  public void setAdminBaseUrls(Map<String, String> adminBaseUrls) {
    this.adminBaseUrls = adminBaseUrls;
  }

  public String getVerifyUiUrl() {
    return verifyUiUrl;
  }

  public void setVerifyUiUrl(String verifyUiUrl) {
    this.verifyUiUrl = verifyUiUrl;
  }

  public Rabbit getRabbit() {
    return rabbit;
  }

  public void setRabbit(Rabbit rabbit) {
    this.rabbit = rabbit;
  }

  public static class Rabbit {
    private String commandsExchange = "chaos.commands.test";
    private String resultsQueue = "chaos.command-results";

    public String getCommandsExchange() {
      return commandsExchange;
    }

    public void setCommandsExchange(String commandsExchange) {
      this.commandsExchange = commandsExchange;
    }

    public String getResultsQueue() {
      return resultsQueue;
    }

    public void setResultsQueue(String resultsQueue) {
      this.resultsQueue = resultsQueue;
    }
  }
}
