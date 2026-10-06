package com.samba.chaos.relay.model;

import java.util.UUID;

/**
 * Result of a reset that waited for a terminal aggregate.
 *
 * <pre>
 * 200 { "commandId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
 *       "commandStatus": "APPLIED" }
 * 409 { "commandId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
 *       "commandStatus": "TIMED_OUT" }
 * </pre>
 */
public record ChaosConfigurationResetResponse(
    UUID commandId, CommandAggregateStatus commandStatus) {}
