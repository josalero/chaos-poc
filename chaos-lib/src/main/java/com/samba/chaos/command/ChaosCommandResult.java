package com.samba.chaos.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/** Result one instance returns after applying a command. */
public record ChaosCommandResult(
    @NotNull UUID commandId,
    @NotBlank String targetApplication,
    @NotBlank String podName,
    @NotNull InstanceOutcome outcome,
    String failedStep,
    Integer httpStatus,
    @NotNull Instant reportedAt) {}
