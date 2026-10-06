package com.samba.chaos.relay.model;

import com.samba.chaos.command.InstanceOutcome;
import java.time.Instant;

/**
 * Result reported by one instance for one command.
 *
 * <p>{@code failedStep} names the actuator call that failed, such as {@code configure} or {@code
 * enable}. It is null when {@code outcome} is SUCCESS.
 */
public record ChaosInstanceStatus(
    String podName,
    InstanceOutcome outcome,
    Instant reportedAt,
    String failedStep,
    Integer httpStatus) {}
