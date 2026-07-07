package com.samba.chaos.relay.console;

import com.samba.chaos.listener.message.ChaosAssaultConfig;
import com.samba.chaos.listener.message.ChaosCommandAction;
import com.samba.chaos.relay.ChaosCommandRequest;
import java.time.Instant;
import java.util.UUID;

public class ChaosCommandForm {

  private String targetApplication;
  private ChaosCommandAction action = ChaosCommandAction.CONFIGURE_AND_ENABLE;
  private String issuedBy = "chaos-console";
  private String correlationId;
  private String expiresAt;
  private String assaultJson;
  private String presetId;

  public String getTargetApplication() {
    return targetApplication;
  }

  public void setTargetApplication(String targetApplication) {
    this.targetApplication = targetApplication;
  }

  public ChaosCommandAction getAction() {
    return action;
  }

  public void setAction(ChaosCommandAction action) {
    this.action = action;
  }

  public String getIssuedBy() {
    return issuedBy;
  }

  public void setIssuedBy(String issuedBy) {
    this.issuedBy = issuedBy;
  }

  public String getCorrelationId() {
    return correlationId;
  }

  public void setCorrelationId(String correlationId) {
    this.correlationId = correlationId;
  }

  public String getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(String expiresAt) {
    this.expiresAt = expiresAt;
  }

  public String getAssaultJson() {
    return assaultJson;
  }

  public void setAssaultJson(String assaultJson) {
    this.assaultJson = assaultJson;
  }

  public String getPresetId() {
    return presetId;
  }

  public void setPresetId(String presetId) {
    this.presetId = presetId;
  }

  public ChaosCommandRequest toRequest(ChaosAssaultConfig assault) {
    Instant expiry = expiresAt != null && !expiresAt.isBlank() ? Instant.parse(expiresAt) : null;
    return new ChaosCommandRequest(
        null,
        "test",
        targetApplication,
        action,
        assault,
        expiry,
        issuedBy,
        correlationId != null && !correlationId.isBlank() ? correlationId : null);
  }
}
