package com.samba.chaos.relay.model;

import java.util.UUID;

/**
 * One row of {@code GET /internal/v1/chaos/services}.
 *
 * <p>{@code configState} is {@link ServiceConfigState#DEFAULT} when the service has no stored
 * command, or when the latest applied command was DISABLE.
 */
public record ChaosServiceStatusSummary(
    String applicationName,
    boolean registryEnabled,
    ServiceConfigState configState,
    Boolean cmEnabled,
    UUID lastCommandId,
    CommandAggregateStatus lastCommandStatus,
    int eurekaUpCount,
    int expectedInstances) {}
