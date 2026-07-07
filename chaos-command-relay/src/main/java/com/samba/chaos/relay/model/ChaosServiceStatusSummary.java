package com.samba.chaos.relay.model;

import java.time.Instant;
import java.util.UUID;

public record ChaosServiceStatusSummary(
    String applicationName,
    boolean registryEnabled,
    ServiceConfigState configState,
    Boolean cmEnabled,
    UUID lastCommandId,
    CommandAggregateStatus lastCommandStatus,
    int eurekaUpCount,
    int expectedInstances) {}
