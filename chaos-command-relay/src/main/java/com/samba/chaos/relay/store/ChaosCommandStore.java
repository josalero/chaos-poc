package com.samba.chaos.relay.store;

import com.samba.chaos.command.ChaosCommandAction;
import com.samba.chaos.command.ChaosCommandResult;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Command history used by status and enable.
 *
 * <p>Records are stored in the relay database. A restart keeps them.
 */
public interface ChaosCommandStore {

  /**
   * Stores a new command. A duplicate id replaces the previous record and its results.
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
   * @param limit maximum number of records
   * @return matching records, empty when none exist
   */
  List<CommandRecord> findByApplication(String applicationName, int limit);

  /**
   * Configure commands for one application, newest {@code publishedAt} first.
   *
   * @param applicationName target application
   * @param actions configure actions to include
   * @return matching records, empty when none exist
   */
  List<CommandRecord> findByApplicationAndActions(
      String applicationName, Collection<ChaosCommandAction> actions);

  /**
   * One page of commands for the named applications, newest {@code publishedAt} first.
   *
   * @param applications allowlisted targets to include
   * @param action action filter, or null for every action
   * @param pageable page index and size, without a sort
   * @return the page, empty when {@code applications} is empty
   */
  Page<CommandRecord> findPage(
      Collection<String> applications, ChaosCommandAction action, Pageable pageable);

  /**
   * Every matching command, newest {@code publishedAt} first.
   *
   * <p>Used when the caller filters by a computed aggregate status.
   *
   * @param applications allowlisted targets to include
   * @param action action filter, or null for every action
   * @return matching records, empty when {@code applications} is empty
   */
  List<CommandRecord> findMatching(Collection<String> applications, ChaosCommandAction action);

  /**
   * Inserts or replaces the result for one instance. Unknown command ids are ignored.
   *
   * @param result outcome reported by one instance, keyed by Eureka instance id
   */
  void addResult(ChaosCommandResult result);
}
