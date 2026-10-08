package com.samba.chaos.relay.store;

import com.samba.chaos.command.ChaosCommandAction;
import com.samba.chaos.command.InstanceOutcome;
import com.samba.chaos.relay.model.InstanceSelection;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One stored command. Instance results live in {@link ChaosCommandResultEntity}. */
@Entity
@Table(name = "chaos_command")
public class ChaosCommandEntity {

  @Id
  @Column(name = "command_id", nullable = false)
  private UUID commandId;

  @Column(name = "target_application", nullable = false)
  private String targetApplication;

  @Enumerated(EnumType.STRING)
  @Column(name = "action", nullable = false, length = 40)
  private ChaosCommandAction action;

  @Column(name = "environment", nullable = false, length = 40)
  private String environment;

  @Column(name = "issued_by", nullable = false)
  private String issuedBy;

  @Column(name = "correlation_id")
  private String correlationId;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "published_at", nullable = false)
  private Instant publishedAt;

  @Column(name = "expected_instances", nullable = false)
  private int expectedInstances;

  @Enumerated(EnumType.STRING)
  @Column(name = "instance_selection", nullable = false, length = 10)
  private InstanceSelection instanceSelection;

  @Column(name = "instance_ids", length = 4000)
  private String instanceIdsJson;

  @Column(name = "assault", length = 8000)
  private String assaultJson;

  @OneToMany(
      mappedBy = "command",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.EAGER)
  private List<ChaosCommandResultEntity> results = new ArrayList<>();

  public UUID getCommandId() {
    return commandId;
  }

  public void setCommandId(UUID commandId) {
    this.commandId = commandId;
  }

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

  public String getEnvironment() {
    return environment;
  }

  public void setEnvironment(String environment) {
    this.environment = environment;
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

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }

  public void setPublishedAt(Instant publishedAt) {
    this.publishedAt = publishedAt;
  }

  public int getExpectedInstances() {
    return expectedInstances;
  }

  public void setExpectedInstances(int expectedInstances) {
    this.expectedInstances = expectedInstances;
  }

  public InstanceSelection getInstanceSelection() {
    return instanceSelection;
  }

  public void setInstanceSelection(InstanceSelection instanceSelection) {
    this.instanceSelection = instanceSelection;
  }

  public String getInstanceIdsJson() {
    return instanceIdsJson;
  }

  public void setInstanceIdsJson(String instanceIdsJson) {
    this.instanceIdsJson = instanceIdsJson;
  }

  public String getAssaultJson() {
    return assaultJson;
  }

  public void setAssaultJson(String assaultJson) {
    this.assaultJson = assaultJson;
  }

  public List<ChaosCommandResultEntity> getResults() {
    return results;
  }

  /**
   * Inserts or replaces the row for this instance id.
   *
   * @param instanceId Eureka instance id
   * @param outcome reported outcome
   * @param reportedAt when the instance answered
   * @param failedStep actuator step that failed, or null
   * @param httpStatus HTTP status, or null
   */
  public void upsertResult(
      String instanceId,
      InstanceOutcome outcome,
      Instant reportedAt,
      String failedStep,
      Integer httpStatus) {
    for (ChaosCommandResultEntity existing : results) {
      if (instanceId.equals(existing.getInstanceId())) {
        existing.apply(outcome, reportedAt, failedStep, httpStatus);
        return;
      }
    }
    ChaosCommandResultEntity created = new ChaosCommandResultEntity();
    created.setCommand(this);
    created.setInstanceId(instanceId);
    created.apply(outcome, reportedAt, failedStep, httpStatus);
    results.add(created);
  }
}
