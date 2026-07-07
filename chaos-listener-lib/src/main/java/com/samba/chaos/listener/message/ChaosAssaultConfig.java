package com.samba.chaos.listener.message;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record ChaosAssaultConfig(
    Integer level,
    Boolean deterministic,
    Boolean exceptionsActive,
    Boolean latencyActive,
    Integer latencyRangeStart,
    Integer latencyRangeEnd,
    List<String> watchedCustomServices,
    JsonNode exception) {}
