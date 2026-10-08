package com.samba.chaos.relay.console;

/**
 * Chaos Monkey actuator read for one Eureka instance.
 *
 * @param instanceId Eureka instance id, or the host when the id is blank
 * @param enabled Chaos Monkey flag, or null when the instance did not answer
 * @param statusJson raw {@code /status} body
 * @param assaultsJson raw {@code /assaults} body
 * @param error transport or HTTP error, safe to show to the operator
 */
public record ChaosInstanceActuatorSnapshot(
    String instanceId, Boolean enabled, String statusJson, String assaultsJson, String error) {}
