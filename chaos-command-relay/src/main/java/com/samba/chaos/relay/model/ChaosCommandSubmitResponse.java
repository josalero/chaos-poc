package com.samba.chaos.relay.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Body of the 202 returned when a command is stored.
 *
 * <pre>
 * {
 *   "commandId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
 *   "status": "PUBLISHED",
 *   "expectedInstances": 2,
 *   "statusUrl": "/internal/v1/chaos/commands/7c9e6679-7425-40de-944b-e07fc1f90ae7"
 * }
 * </pre>
 *
 * <p>Poll {@code statusUrl} until the aggregate is APPLIED, FAILED, or TIMED_OUT.
 */
public record ChaosCommandSubmitResponse(
    UUID commandId,
    CommandAggregateStatus status,
    Instant publishedAt,
    String targetApplication,
    int expectedInstances,
    String statusUrl,
    String correlationId) {}
