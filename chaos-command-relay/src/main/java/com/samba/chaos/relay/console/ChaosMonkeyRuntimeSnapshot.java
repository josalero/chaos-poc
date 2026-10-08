package com.samba.chaos.relay.console;

import java.util.List;

/**
 * Live Chaos Monkey actuator read for one application.
 *
 * <pre>
 * { "configured": true, "reachable": true, "enabled": false, "instances": [] }
 * </pre>
 *
 * <p>{@code statusJson} and {@code assaultsJson} are the raw actuator bodies of the first instance
 * that answered. {@code enabled} is true when any instance reports Chaos Monkey on. {@code
 * instances} keeps one row per UP instance, including instances that failed to answer.
 */
public record ChaosMonkeyRuntimeSnapshot(
    boolean configured,
    boolean reachable,
    Boolean enabled,
    String statusJson,
    String assaultsJson,
    String errorMessage,
    List<ChaosInstanceActuatorSnapshot> instances) {

  /**
   * Snapshot used when discovery has no UP instances.
   *
   * @return a snapshot with {@code configured} false
   */
  public static ChaosMonkeyRuntimeSnapshot unconfigured() {
    return new ChaosMonkeyRuntimeSnapshot(false, false, null, null, null, null, List.of());
  }

  /**
   * Snapshot used when every actuator call failed.
   *
   * @param message transport or HTTP error, safe to show to the operator
   * @param instances one row per UP instance
   * @return a snapshot with {@code reachable} false
   */
  public static ChaosMonkeyRuntimeSnapshot unreachable(
      String message, List<ChaosInstanceActuatorSnapshot> instances) {
    return new ChaosMonkeyRuntimeSnapshot(true, false, null, null, null, message, instances);
  }

  /**
   * Snapshot used when at least one instance answered.
   *
   * @param enabled true when any probed instance reports Chaos Monkey enabled
   * @param statusJson raw status body of the first instance that answered
   * @param assaultsJson raw assaults body of that instance
   * @return a reachable snapshot with no per-instance rows
   */
  public static ChaosMonkeyRuntimeSnapshot ok(
      boolean enabled, String statusJson, String assaultsJson) {
    return ok(enabled, statusJson, assaultsJson, List.of());
  }

  /**
   * Snapshot used when at least one instance answered.
   *
   * @param enabled true when any probed instance reports Chaos Monkey enabled
   * @param statusJson raw status body of the first instance that answered
   * @param assaultsJson raw assaults body of that instance
   * @param instances one row per UP instance
   * @return a reachable snapshot
   */
  public static ChaosMonkeyRuntimeSnapshot ok(
      boolean enabled,
      String statusJson,
      String assaultsJson,
      List<ChaosInstanceActuatorSnapshot> instances) {
    return new ChaosMonkeyRuntimeSnapshot(
        true, true, enabled, statusJson, assaultsJson, null, instances);
  }
}
