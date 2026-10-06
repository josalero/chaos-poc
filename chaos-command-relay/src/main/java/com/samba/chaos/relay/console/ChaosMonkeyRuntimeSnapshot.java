package com.samba.chaos.relay.console;

/**
 * Live Chaos Monkey actuator read for one application.
 *
 * <pre>
 * { "configured": true, "reachable": true, "enabled": false }
 * </pre>
 *
 * <p>{@code statusJson} and {@code assaultsJson} are the raw actuator bodies of the first instance
 * that answered.
 */
public record ChaosMonkeyRuntimeSnapshot(
    boolean configured,
    boolean reachable,
    Boolean enabled,
    String statusJson,
    String assaultsJson,
    String errorMessage) {

  /**
   * Snapshot used when discovery has no UP instances.
   *
   * @return a snapshot with {@code configured} false
   */
  public static ChaosMonkeyRuntimeSnapshot unconfigured() {
    return new ChaosMonkeyRuntimeSnapshot(false, false, null, null, null, null);
  }

  /**
   * Snapshot used when every actuator call failed.
   *
   * @param message transport or HTTP error, safe to show to the operator
   * @return a snapshot with {@code reachable} false
   */
  public static ChaosMonkeyRuntimeSnapshot unreachable(String message) {
    return new ChaosMonkeyRuntimeSnapshot(true, false, null, null, null, message);
  }

  /**
   * Snapshot used when at least one instance answered.
   *
   * @param enabled true when any probed instance reports Chaos Monkey enabled
   * @param statusJson raw status body
   * @param assaultsJson raw assaults body
   * @return a reachable snapshot
   */
  public static ChaosMonkeyRuntimeSnapshot ok(
      boolean enabled, String statusJson, String assaultsJson) {
    return new ChaosMonkeyRuntimeSnapshot(true, true, enabled, statusJson, assaultsJson, null);
  }
}
