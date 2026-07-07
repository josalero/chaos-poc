package com.samba.chaos.relay.model;

import com.samba.chaos.listener.message.ChaosAssaultConfig;
import com.samba.chaos.listener.message.ChaosCommandAction;
import java.time.Instant;
import java.util.UUID;

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
