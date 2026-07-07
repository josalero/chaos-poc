package com.samba.chaos.relay;

import com.samba.chaos.listener.message.ChaosAssaultConfig;
import com.samba.chaos.listener.message.ChaosCommandAction;
import com.samba.chaos.listener.message.ChaosCommandResult;
import com.samba.chaos.relay.model.ChaosInstanceStatus;
import com.samba.chaos.relay.model.CommandAggregateStatus;
import com.samba.chaos.listener.message.InstanceOutcome;
import com.samba.chaos.relay.InMemoryChaosCommandStore.CommandRecord;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

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

  public record CommandRecord(
      UUID commandId,
      ChaosCommandAction action,
      String targetApplication,
      int expectedInstances,
      Instant publishedAt,
      String correlationId,
      String issuedBy,
      Instant expiresAt,
      ChaosAssaultConfig assault,
      List<ChaosInstanceStatus> instances) {

    CommandRecord withInstances(List<ChaosInstanceStatus> newInstances) {
      return new CommandRecord(
          commandId,
          action,
          targetApplication,
          expectedInstances,
          publishedAt,
          correlationId,
          issuedBy,
          expiresAt,
          assault,
          List.copyOf(newInstances));
    }

    public int successCount() {
      return (int)
          instances.stream().filter(i -> i.outcome() == InstanceOutcome.SUCCESS).count();
    }

    public int failureCount() {
      return (int)
          instances.stream().filter(i -> i.outcome() == InstanceOutcome.ACTUATOR_ERROR).count();
    }
  }
}
