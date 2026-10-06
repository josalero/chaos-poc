package com.samba.chaos.relay.store;

import com.samba.chaos.command.ChaosCommandResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Command history used by status, enable, and cleanup.
 *
 * <p>The default implementation keeps records in memory. A restart loses them.
 */
public interface ChaosCommandStore {

  /**
   * Stores a new command. A duplicate id replaces the previous record.
   *
   * @param record command and its expected instance count
   */
  void save(CommandRecord record);

  /**
   * Looks up one command.
   *
   * @param commandId stored id
   * @return empty when the id is unknown
   */
  Optional<CommandRecord> findById(UUID commandId);

  /**
   * Newest command for one application, by {@code publishedAt}.
   *
   * @param applicationName target application
   * @return empty when that application has no commands
   */
  Optional<CommandRecord> findLatestByApplication(String applicationName);

  /**
   * Commands for one application, newest {@code publishedAt} first.
   *
   * @param applicationName target application
   * @return matching records, empty when none exist
   */
  List<CommandRecord> findByApplication(String applicationName);

  /**
   * Every stored command. Order is not significant.
   *
   * @return all records, empty when the store has none
   */
  List<CommandRecord> findAll();

  /**
   * Appends one instance result. Unknown command ids are ignored.
   *
   * @param result outcome reported by one instance
   */
  void addResult(ChaosCommandResult result);

  /**
   * Removes one command and its instance results.
   *
   * @param commandId stored id
   */
  void delete(UUID commandId);

  /**
   * Removes records published before the cutoff.
   *
   * @param cutoff exclusive upper bound on {@code publishedAt}
   * @return number of records removed
   */
  int deletePublishedBefore(Instant cutoff);
}
