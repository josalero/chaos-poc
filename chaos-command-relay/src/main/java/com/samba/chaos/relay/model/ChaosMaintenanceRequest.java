package com.samba.chaos.relay.model;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public record ChaosMaintenanceRequest(
    @NotBlank String issuedBy, String correlationId, Instant expiresAt) {}
