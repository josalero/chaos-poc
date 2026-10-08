package com.samba.chaos.relay.console;

import com.samba.chaos.relay.config.ChaosRelayProperties;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cached Chaos Monkey on/off state for the service list.
 *
 * <p>A background refresh asks each UP pod for {@code /actuator/chaosmonkey/status}. The list
 * endpoint reads this cache and does not call the actuator.
 */
@Component
public class ChaosMonkeyStatusCache {

  private static final Logger log = LoggerFactory.getLogger(ChaosMonkeyStatusCache.class);

  private final ChaosRelayProperties properties;
  private final ChaosMonkeyActuatorProbe actuatorProbe;
  private final Map<String, Entry> entries = new ConcurrentHashMap<>();

  /**
   * Creates the cache.
   *
   * @param properties allowlist
   * @param actuatorProbe status-only reads
   */
  public ChaosMonkeyStatusCache(
      ChaosRelayProperties properties, ChaosMonkeyActuatorProbe actuatorProbe) {
    this.properties = properties;
    this.actuatorProbe = actuatorProbe;
  }

  /** Refreshes every allowlisted application. */
  @Scheduled(fixedDelayString = "${chaos.relay.status-refresh-interval-ms:20000}")
  public void refreshAll() {
    for (String applicationName : properties.getAllowedTargetApplications()) {
      refresh(applicationName);
    }
  }

  /**
   * Refreshes one application now.
   *
   * @param applicationName Eureka application name
   */
  public void refresh(String applicationName) {
    try {
      ChaosMonkeyActuatorProbe.EnabledRead read = actuatorProbe.readEnabled(applicationName);
      entries.put(
          applicationName,
          new Entry(read.enabled(), read.reachableInstances(), read.upInstances(), Instant.now()));
    } catch (RuntimeException ex) {
      log.warn("Chaos Monkey status refresh failed for {}", applicationName);
    }
  }

  /**
   * Last refresh for one application.
   *
   * @param applicationName Eureka application name
   * @return empty until the first successful refresh attempt
   */
  public Optional<Entry> get(String applicationName) {
    return Optional.ofNullable(entries.get(applicationName));
  }

  /**
   * One cached status read.
   *
   * @param enabled true when any pod reported Chaos Monkey on; null when none answered
   * @param reachableInstances pods that returned status
   * @param upInstances pods discovery listed as UP
   * @param checkedAt when this entry was written
   */
  public record Entry(
      Boolean enabled, int reachableInstances, int upInstances, Instant checkedAt) {}
}
