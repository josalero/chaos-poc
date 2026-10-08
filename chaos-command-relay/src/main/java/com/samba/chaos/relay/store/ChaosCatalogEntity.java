package com.samba.chaos.relay.store;

import com.samba.chaos.command.ChaosCommandAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/** One saved scenario for a single allowlisted service. */
@Entity
@Table(
    name = "chaos_catalog_entry",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_chaos_catalog_label",
            columnNames = {"target_application", "label"}))
public class ChaosCatalogEntity {

  @Id
  @Column(name = "catalog_id", nullable = false)
  private UUID catalogId;

  @Column(name = "target_application", nullable = false)
  private String targetApplication;

  @Column(name = "label", nullable = false)
  private String label;

  @Enumerated(EnumType.STRING)
  @Column(name = "action", nullable = false, length = 40)
  private ChaosCommandAction action;

  @Column(name = "assault", length = 8000)
  private String assaultJson;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public UUID getCatalogId() {
    return catalogId;
  }

  public void setCatalogId(UUID catalogId) {
    this.catalogId = catalogId;
  }

  public String getTargetApplication() {
    return targetApplication;
  }

  public void setTargetApplication(String targetApplication) {
    this.targetApplication = targetApplication;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public ChaosCommandAction getAction() {
    return action;
  }

  public void setAction(ChaosCommandAction action) {
    this.action = action;
  }

  public String getAssaultJson() {
    return assaultJson;
  }

  public void setAssaultJson(String assaultJson) {
    this.assaultJson = assaultJson;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
