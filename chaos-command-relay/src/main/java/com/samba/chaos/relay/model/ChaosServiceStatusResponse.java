package com.samba.chaos.relay.model;

import com.samba.chaos.command.ChaosAssaultConfig;
import com.samba.chaos.command.ChaosCommandAction;
import java.time.Instant;
import java.util.UUID;

/**
 * Relay view of one allowlisted service and its latest command.
 *
 * <p>{@code cmEnabled} is the live actuator flag when an instance answers. Otherwise it is derived
 * from the latest APPLIED enable or configure-and-enable command.
 */
public record ChaosServiceStatusResponse(
    String applicationName,
    boolean registryEnabled,
    ServiceConfigState configState,
    Boolean cmEnabled,
    UUID lastCommandId,
    CommandAggregateStatus lastCommandStatus,
    ChaosCommandAction lastCommandAction,
    Instant lastPublishedAt,
    String lastIssuedBy,
    Instant expiresAt,
    int eurekaUpCount,
    int expectedInstances,
    ChaosAssaultConfig desiredAssault,
    ChaosAssaultConfig appliedAssault) {}
