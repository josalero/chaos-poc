package com.samba.chaos.relay.model;

import com.samba.chaos.command.ChaosAssaultConfig;
import com.samba.chaos.command.ChaosCommandAction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code POST /internal/v1/chaos/services/{applicationName}/catalog}.
 *
 * <p>Saving the same label again replaces the action and assault. {@code assault} is omitted for
 * {@code DISABLE}.
 *
 * @param label name shown on the Apply button
 * @param action command action stored with the entry
 * @param assault assault body, or null for disable
 */
public record ChaosCatalogSaveRequest(
    @NotBlank String label,
    @NotNull ChaosCommandAction action,
    @Valid ChaosAssaultConfig assault) {}
