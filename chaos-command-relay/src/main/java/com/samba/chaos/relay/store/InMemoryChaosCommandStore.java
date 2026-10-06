package com.samba.chaos.relay.store;

import com.samba.chaos.command.ChaosCommandResult;
import com.samba.chaos.relay.model.ChaosInstanceStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * {@link ChaosCommandStore} backed by a concurrent map.
 *
 * <p>Instance results are appended on the record itself. This store does not persist them.
 */
@Component
public class InMemoryChaosCommandStore implements ChaosCommandStore {

  private final Map<UUID, CommandRecord> commands = new ConcurrentHashMap<>();

  @Override
  public void save(CommandRecord record) {
    commands.put(record.commandId(), record);
  }

  @Override
  public Optional<CommandRecord> findById(UUID commandId) {
    return Optional.ofNullable(commands.get(commandId));
  }

  @Override
  public Optional<CommandRecord> findLatestByApplication(String applicationName) {
    return commands.values().stream()
        .filter(record -> applicationName.equals(record.targetApplication()))
        .max(Comparator.comparing(CommandRecord::publishedAt));
  }

  @Override
  public List<CommandRecord> findByApplication(String applicationName) {
    return commands.values().stream()
        .filter(record -> applicationName.equals(record.targetApplication()))
        .sorted(Comparator.comparing(CommandRecord::publishedAt).reversed())
        .toList();
  }

  @Override
  public List<CommandRecord> findAll() {
    return List.copyOf(commands.values());
  }

  @Override
  public void addResult(ChaosCommandResult result) {
    commands.computeIfPresent(
        result.commandId(),
        (id, record) -> {
          List<ChaosInstanceStatus> instances = new ArrayList<>(record.instances());
          instances.removeIf(i -> i.podName().equals(result.podName()));
          instances.add(
              new ChaosInstanceStatus(
                  result.podName(),
                  result.outcome(),
                  result.reportedAt(),
                  result.failedStep(),
                  result.httpStatus()));
          return record.withInstances(instances);
        });
  }

  @Override
  public void delete(UUID commandId) {
    commands.remove(commandId);
  }

  @Override
  public int deletePublishedBefore(Instant cutoff) {
    int sizeBefore = commands.size();
    commands.entrySet().removeIf(entry -> entry.getValue().publishedAt().isBefore(cutoff));
    return sizeBefore - commands.size();
  }
}
