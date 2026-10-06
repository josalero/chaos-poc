package com.samba.chaos.relay.model;

import com.samba.chaos.command.ChaosAssaultConfig;
import com.samba.chaos.command.ChaosCommandAction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Body of {@code POST /internal/v1/chaos/commands}.
 *
 * <pre>
 * {
 *   "environment": "test",
 *   "targetApplication": "chaos-poc-demo",
 *   "action": "CONFIGURE_AND_ENABLE",
 *   "issuedBy": "chaos-console",
 *   "expiresAt": "2026-10-06T18:00:00Z",
 *   "assault": { "level": 1, "latencyActive": true },
 *   "instanceSelection": "SOME",
 *   "instanceIds": ["chaos-poc-demo-1"]
 * }
 * </pre>
 *
 * <p>{@code commandId} may be omitted. {@code environment} must be {@code test}. {@code expiresAt}
 * is required for ENABLE and CONFIGURE_AND_ENABLE. {@code assault} is required for CONFIGURE and
 * CONFIGURE_AND_ENABLE. Omit {@code instanceSelection} to reach every UP instance. {@code SOME}
 * requires {@code instanceIds} that are UP now. Those ids are the discovery instance ids.
 */
public record ChaosCommandRequest(
    UUID commandId,
    @NotBlank String environment,
    @NotBlank String targetApplication,
    @NotNull ChaosCommandAction action,
    @Valid ChaosAssaultConfig assault,
    Instant expiresAt,
    @NotBlank String issuedBy,
    String correlationId,
    InstanceSelection instanceSelection,
    List<String> instanceIds) {

  /**
   * Command for every UP instance.
   *
   * @param commandId optional id; the relay generates one when null
   * @param environment must be {@code test}
   * @param targetApplication allowlisted Eureka application name
   * @param action command action
   * @param assault assault body, required for configure actions
   * @param expiresAt lease end, required for enable actions
   * @param issuedBy operator or client name
   * @param correlationId optional caller reference
   */
  public ChaosCommandRequest(
      UUID commandId,
      String environment,
      String targetApplication,
      ChaosCommandAction action,
      ChaosAssaultConfig assault,
      Instant expiresAt,
      String issuedBy,
      String correlationId) {
    this(
        commandId,
        environment,
        targetApplication,
        action,
        assault,
        expiresAt,
        issuedBy,
        correlationId,
        null,
        null);
  }
}
