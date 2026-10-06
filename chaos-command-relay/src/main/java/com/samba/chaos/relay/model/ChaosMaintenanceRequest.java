package com.samba.chaos.relay.model;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

/**
 * Body of enable, disable, and reset.
 *
 * <pre>
 * { "issuedBy": "chaos-console", "expiresAt": "2026-10-06T18:00:00Z" }
 * </pre>
 *
 * <p>{@code expiresAt} is required for enable. Disable and reset ignore it.
 */
public record ChaosMaintenanceRequest(
    @NotBlank String issuedBy, String correlationId, Instant expiresAt) {}
