package com.samba.chaos.relay.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChaosCommandSubmitResponse(
    UUID commandId,
    CommandAggregateStatus status,
    Instant publishedAt,
    String targetApplication,
    int expectedInstances,
    String statusUrl,
    String correlationId) {}
