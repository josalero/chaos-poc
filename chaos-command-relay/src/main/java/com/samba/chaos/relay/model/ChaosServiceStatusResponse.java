package com.samba.chaos.relay.model;

import com.samba.chaos.command.ChaosAssaultConfig;
import com.samba.chaos.command.ChaosCommandAction;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Relay view of one allowlisted service and its latest command.
 *
 * <p>{@code cmEnabled} is the live actuator flag when an instance answers. Otherwise it is derived
 * from the latest APPLIED enable or configure-and-enable command. {@code upInstanceIds} are the
 * discovery ids a SOME command can name.
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
    ChaosAssaultConfig appliedAssault,
    List<String> upInstanceIds) {}
