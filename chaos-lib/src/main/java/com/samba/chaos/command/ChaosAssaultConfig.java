package com.samba.chaos.command;

import java.util.List;
import tools.jackson.databind.JsonNode;

/** Assault fields posted to the Chaos Monkey actuator. Omitted fields use actuator defaults. */
public record ChaosAssaultConfig(
    Integer level,
    Boolean deterministic,
    Boolean exceptionsActive,
    Boolean latencyActive,
    Integer latencyRangeStart,
    Integer latencyRangeEnd,
    List<String> watchedCustomServices,
    JsonNode exception) {

  /** Copies {@code watchedCustomServices} so callers cannot change the stored list. */
  public ChaosAssaultConfig {
    watchedCustomServices =
        watchedCustomServices == null ? null : List.copyOf(watchedCustomServices);
  }
}
