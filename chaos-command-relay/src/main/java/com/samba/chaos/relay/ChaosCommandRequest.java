package com.samba.chaos.relay;

import com.samba.chaos.listener.message.ChaosAssaultConfig;
import com.samba.chaos.listener.message.ChaosCommandAction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record ChaosCommandRequest(
    UUID commandId,
    @NotBlank String environment,
    @NotBlank String targetApplication,
    @NotNull ChaosCommandAction action,
    @Valid ChaosAssaultConfig assault,
    Instant expiresAt,
    @NotBlank String issuedBy,
    String correlationId) {}
