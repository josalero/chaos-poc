package com.samba.chaos.relay.console;

import com.samba.chaos.listener.message.ChaosCommandAction;
import com.samba.chaos.relay.model.CommandAggregateStatus;
import java.time.Instant;
import java.util.UUID;

public record ChaosCommandHistoryEntry(
    UUID commandId,
    ChaosCommandAction action,
    CommandAggregateStatus status,
    Instant publishedAt,
    String issuedBy,
    Instant expiresAt,
    int successCount,
    int failureCount) {}
