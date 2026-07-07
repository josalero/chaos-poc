package com.samba.chaos.listener.message;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record ChaosCommandMessage(
    UUID commandId,
    @NotBlank String environment,
    @NotBlank String targetApplication,
    @NotNull ChaosCommandAction action,
    @Valid ChaosAssaultConfig assault,
    Instant expiresAt,
    @NotBlank String issuedBy,
    String correlationId) {}
