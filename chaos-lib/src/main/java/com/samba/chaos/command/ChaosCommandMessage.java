package com.samba.chaos.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/** Command the relay posts to every UP instance of the target application. */
public record ChaosCommandMessage(
    UUID commandId,
    @NotBlank String environment,
    @NotBlank String targetApplication,
    @NotNull ChaosCommandAction action,
    @Valid ChaosAssaultConfig assault,
    Instant expiresAt,
    @NotBlank String issuedBy,
    String correlationId) {}
