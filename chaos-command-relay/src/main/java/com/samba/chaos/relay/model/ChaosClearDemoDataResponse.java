package com.samba.chaos.relay.model;

/**
 * Result of clearing demo orders on every UP instance.
 *
 * <pre>
 * { "cleared": true, "message": "Demo data cleared" }
 * </pre>
 */
public record ChaosClearDemoDataResponse(boolean cleared, String message) {}
