package com.samba.chaos.relay.model;

import com.samba.chaos.command.ChaosAssaultConfig;
import com.samba.chaos.command.ChaosCommandAction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/**
 * Body of {@code POST /internal/v1/chaos/commands}.
 *
 * <pre>
 * {
 *   "environment": "test",
 *   "targetApplication": "chaos-poc-demo",
 *   "action": "CONFIGURE_AND_ENABLE",
 *   "issuedBy": "chaos-console",
 *   "expiresAt": "2026-10-06T18:00:00Z",
 *   "assault": { "level": 1, "latencyActive": true }
 * }
 * </pre>
 *
 * <p>{@code commandId} may be omitted. {@code environment} must be {@code test}. {@code expiresAt}
 * is required for ENABLE and CONFIGURE_AND_ENABLE. {@code assault} is required for CONFIGURE and
 * CONFIGURE_AND_ENABLE.
 */
public record ChaosCommandRequest(
    UUID commandId,
    @NotBlank String environment,
    @NotBlank String targetApplication,
    @NotNull ChaosCommandAction action,
    @Valid ChaosAssaultConfig assault,
    Instant expiresAt,
    @NotBlank String issuedBy,
    String correlationId) {}
