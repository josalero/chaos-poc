package com.samba.chaos.relay.model;

import com.samba.chaos.listener.message.InstanceOutcome;
import java.time.Instant;

public record ChaosInstanceStatus(
    String podName, InstanceOutcome outcome, Instant reportedAt, String failedStep, Integer httpStatus) {}
