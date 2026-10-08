package com.samba.chaos.relay.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under {@code chaos.relay}.
 *
 * <p>{@code allowedTargetApplications} is the only set of Eureka names the relay will command.
 * {@code statusTimeoutSeconds} closes an in-flight aggregate as TIMED_OUT (default 30). Stored
 * commands are kept.
 */
@ConfigurationProperties(prefix = "chaos.relay")
public class ChaosRelayProperties {

  private List<String> allowedTargetApplications = new ArrayList<>();
  private int statusTimeoutSeconds = 30;
  private String verifyUiUrl = "http://localhost:18000";

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

  public String getVerifyUiUrl() {
    return verifyUiUrl;
  }

  public void setVerifyUiUrl(String verifyUiUrl) {
    this.verifyUiUrl = verifyUiUrl;
  }
}
