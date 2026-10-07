package com.samba.chaos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds {@code samba.chaos.command.*}. */
@ConfigurationProperties(prefix = "samba.chaos.command")
public class ChaosProperties {

  private boolean enabled;
  private String environment = "test";
  private String applicationName;
  private String podName = "local";
  private String actuatorBaseUrl;
  private int maxApplyAttempts = 3;
  private long applyBackoffMs = 200;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getEnvironment() {
    return environment;
  }

  public void setEnvironment(String environment) {
    this.environment = environment;
  }

  public String getApplicationName() {
    return applicationName;
  }

  public void setApplicationName(String applicationName) {
    this.applicationName = applicationName;
  }

  public String getPodName() {
    return podName;
  }

  public void setPodName(String podName) {
    this.podName = podName;
  }

  public String getActuatorBaseUrl() {
    return actuatorBaseUrl;
  }

  public void setActuatorBaseUrl(String actuatorBaseUrl) {
    this.actuatorBaseUrl = actuatorBaseUrl;
  }

  public int getMaxApplyAttempts() {
    return maxApplyAttempts;
  }

  public void setMaxApplyAttempts(int maxApplyAttempts) {
    this.maxApplyAttempts = maxApplyAttempts;
  }

  public long getApplyBackoffMs() {
    return applyBackoffMs;
  }

  public void setApplyBackoffMs(long applyBackoffMs) {
    this.applyBackoffMs = applyBackoffMs;
  }
}
