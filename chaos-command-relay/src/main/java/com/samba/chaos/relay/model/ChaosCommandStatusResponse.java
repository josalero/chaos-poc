package com.samba.chaos.relay.model;

import com.samba.chaos.listener.message.ChaosCommandAction;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
    List<ChaosInstanceStatus> instances) {}
