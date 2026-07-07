package com.samba.chaos.relay.console;

import com.samba.chaos.relay.model.ChaosMaintenanceRequest;
import java.time.Instant;

public class ChaosMaintenanceForm {

  private String issuedBy = "chaos-console";
  private String correlationId;
  private String expiresAt;

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

  public ChaosMaintenanceRequest toRequest() {
    Instant expiry = expiresAt != null && !expiresAt.isBlank() ? Instant.parse(expiresAt) : null;
    return new ChaosMaintenanceRequest(
        issuedBy,
        correlationId != null && !correlationId.isBlank() ? correlationId : null,
        expiry);
  }
}
