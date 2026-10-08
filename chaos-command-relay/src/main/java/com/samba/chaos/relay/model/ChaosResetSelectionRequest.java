package com.samba.chaos.relay.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Selected services to turn off. An empty list is rejected and publishes nothing.
 *
 * <pre>
 * { "issuedBy": "chaos-console",
 *   "applicationNames": ["chaos-poc-demo", "chaos-poc-downstream"] }
 * </pre>
 *
 * @param issuedBy operator name stored on each command
 * @param applicationNames allowlisted names, duplicates ignored
 */
public record ChaosResetSelectionRequest(
    @NotBlank String issuedBy, @NotNull List<String> applicationNames) {}
