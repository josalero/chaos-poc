package com.samba.chaos.relay.store;

import com.samba.chaos.command.InstanceOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/** One instance result for a stored command. */
@Entity
@Table(
    name = "chaos_command_instance_result",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_chaos_result_instance",
            columnNames = {"command_id", "instance_id"}))
public class ChaosCommandResultEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "command_id", nullable = false)
  private ChaosCommandEntity command;

  @Column(name = "instance_id", nullable = false)
  private String instanceId;

  @Enumerated(EnumType.STRING)
  @Column(name = "outcome", nullable = false, length = 40)
  private InstanceOutcome outcome;

  @Column(name = "reported_at", nullable = false)
  private Instant reportedAt;

  @Column(name = "failed_step")
  private String failedStep;

  @Column(name = "http_status")
  private Integer httpStatus;

  public Long getId() {
    return id;
  }

  public ChaosCommandEntity getCommand() {
    return command;
  }

  public void setCommand(ChaosCommandEntity command) {
    this.command = command;
  }

  public String getInstanceId() {
    return instanceId;
  }

  public void setInstanceId(String instanceId) {
    this.instanceId = instanceId;
  }

  public InstanceOutcome getOutcome() {
    return outcome;
  }

  public Instant getReportedAt() {
    return reportedAt;
  }

  public String getFailedStep() {
    return failedStep;
  }

  public Integer getHttpStatus() {
    return httpStatus;
  }

  /**
   * Copies a reported outcome onto this row.
   *
   * @param reportedOutcome instance outcome
   * @param reportedAt when the instance answered
   * @param failedStep actuator step that failed, or null
   * @param httpStatus HTTP status, or null
   */
  public void apply(
      InstanceOutcome reportedOutcome, Instant reportedAt, String failedStep, Integer httpStatus) {
    this.outcome = reportedOutcome;
    this.reportedAt = reportedAt;
    this.failedStep = failedStep;
    this.httpStatus = httpStatus;
  }
}
