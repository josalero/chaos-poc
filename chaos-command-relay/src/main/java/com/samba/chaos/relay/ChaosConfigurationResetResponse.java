package com.samba.chaos.relay;

import com.samba.chaos.relay.model.CommandAggregateStatus;
import java.util.UUID;

public record ChaosConfigurationResetResponse(
    UUID commandId, CommandAggregateStatus commandStatus) {}
