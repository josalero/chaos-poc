package com.samba.chaos.relay.model;

import com.samba.chaos.command.ChaosAssaultConfig;
import com.samba.chaos.command.ChaosCommandAction;
import java.time.Instant;
import java.util.UUID;

/**
 * One saved scenario for a service.
 *
 * @param catalogId stable id
 * @param targetApplication service the entry belongs to
 * @param label name shown on the Apply button
 * @param action command action
 * @param assault assault body, or null for disable
 * @param createdAt when the entry was first saved
 */
public record ChaosCatalogEntry(
    UUID catalogId,
    String targetApplication,
    String label,
    ChaosCommandAction action,
    ChaosAssaultConfig assault,
    Instant createdAt) {}
