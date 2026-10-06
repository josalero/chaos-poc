package com.samba.chaos.relay.model;

import com.samba.chaos.command.ChaosAssaultConfig;
import com.samba.chaos.command.ChaosCommandAction;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate status of one command, including the assault the console renders as YAML.
 *
 * <pre>
 * GET /internal/v1/chaos/commands/{commandId}
 * {
 *   "status": "PARTIAL",
 *   "expectedInstances": 2,
 *   "successCount": 1,
 *   "failureCount": 0
 * }
 * </pre>
 *
 * <p>{@code completedAt} is set only when the aggregate is APPLIED, FAILED, or TIMED_OUT. {@code
 * instanceSelection} is ALL when the command omitted a scope. {@code expectedInstances} is the
 * selected count, so SOME can be APPLIED while other replicas stay untouched.
 */
public record ChaosCommandStatusResponse(
    UUID commandId,
    CommandAggregateStatus status,
    String targetApplication,
    ChaosCommandAction action,
    int expectedInstances,
    int successCount,
    int failureCount,
    Instant publishedAt,
    Instant completedAt,
    String correlationId,
    String issuedBy,
    Instant expiresAt,
    ChaosAssaultConfig assault,
    List<ChaosInstanceStatus> instances,
    InstanceSelection instanceSelection,
    List<String> instanceIds) {}
