package com.samba.chaos.listener.message;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record ChaosCommandResult(
    @NotNull UUID commandId,
    @NotBlank String targetApplication,
    @NotBlank String podName,
    @NotNull InstanceOutcome outcome,
    String failedStep,
    Integer httpStatus,
    @NotNull Instant reportedAt) {}
