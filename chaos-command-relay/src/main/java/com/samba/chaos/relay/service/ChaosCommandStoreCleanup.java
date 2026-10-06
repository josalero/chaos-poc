package com.samba.chaos.relay.service;

import com.samba.chaos.relay.config.ChaosRelayProperties;
import com.samba.chaos.relay.store.ChaosCommandStore;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drops stored commands older than {@code chaos.relay.command-ttl-hours}.
 *
 * <p>The schedule is {@code chaos.relay.command-cleanup-interval-ms}, one hour by default. The
 * store is in memory, so a restart already drops everything.
 */
@Component
public class ChaosCommandStoreCleanup {

  private static final Logger log = LoggerFactory.getLogger(ChaosCommandStoreCleanup.class);
  private final ChaosCommandStore commandStore;
  private final ChaosRelayProperties properties;

  /**
   * Creates the cleanup task.
   *
   * @param commandStore store to prune
   * @param properties TTL in hours
   */
  public ChaosCommandStoreCleanup(ChaosCommandStore commandStore, ChaosRelayProperties properties) {
    this.commandStore = commandStore;
    this.properties = properties;
  }

  /** Deletes commands whose {@code publishedAt} is older than the configured TTL. */
  @Scheduled(fixedDelayString = "${chaos.relay.command-cleanup-interval-ms:3600000}")
  public void removeExpiredHistory() {
    Instant cutoff = Instant.now().minus(properties.getCommandTtlHours(), ChronoUnit.HOURS);
    int deleted = commandStore.deletePublishedBefore(cutoff);
    if (deleted > 0) {
      log.info("Removed {} expired chaos command records", deleted);
    }
  }
}
