package com.samba.chaos.relay.console;

public record ChaosMonkeyRuntimeSnapshot(
    boolean configured,
    boolean reachable,
    Boolean enabled,
    String statusJson,
    String assaultsJson,
    String errorMessage) {

  public static ChaosMonkeyRuntimeSnapshot unconfigured() {
    return new ChaosMonkeyRuntimeSnapshot(false, false, null, null, null, null);
  }

  public static ChaosMonkeyRuntimeSnapshot unreachable(String message) {
    return new ChaosMonkeyRuntimeSnapshot(true, false, null, null, null, message);
  }

  public static ChaosMonkeyRuntimeSnapshot ok(
      boolean enabled, String statusJson, String assaultsJson) {
    return new ChaosMonkeyRuntimeSnapshot(true, true, enabled, statusJson, assaultsJson, null);
  }
}
