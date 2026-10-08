package com.samba.chaos.relay.model;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of {@code GET /internal/v1/chaos/services}.
 *
 * <p>{@code configState} is {@link ServiceConfigState#DEFAULT} when the service has no stored
 * command, or when the latest applied command was DISABLE. {@code cmEnabled} is null until the
 * status cache has an answer. {@code cmCheckedAt} is when that answer was read.
 */
public record ChaosServiceStatusSummary(
    String applicationName,
    boolean registryEnabled,
    ServiceConfigState configState,
    Boolean cmEnabled,
    UUID lastCommandId,
    CommandAggregateStatus lastCommandStatus,
    int eurekaUpCount,
    int expectedInstances,
    Instant cmCheckedAt) {}
