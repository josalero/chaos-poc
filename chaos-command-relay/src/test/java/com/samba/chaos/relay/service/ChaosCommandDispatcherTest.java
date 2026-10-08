package com.samba.chaos.relay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.samba.chaos.command.ChaosCommandAction;
import com.samba.chaos.command.ChaosCommandMessage;
import com.samba.chaos.command.ChaosCommandResult;
import com.samba.chaos.command.InstanceOutcome;
import com.samba.chaos.relay.client.ChaosCommandClient;
import com.samba.chaos.relay.config.ChaosRelayProperties;
import com.samba.chaos.relay.console.ChaosMonkeyStatusCache;
import com.samba.chaos.relay.model.CommandAggregateStatus;
import com.samba.chaos.relay.store.ChaosCommandStore;
import com.samba.chaos.relay.store.CommandRecord;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

class ChaosCommandDispatcherTest {

  @Test
  void keepsBothResultsWhenReplicasReportTheSamePodName() {
    RecordingStore store = new RecordingStore();
    ChaosCommandClient client = mock(ChaosCommandClient.class);
    when(client.apply(any(), any()))
        .thenAnswer(
            invocation -> {
              ChaosCommandMessage message = invocation.getArgument(1);
              return new ChaosCommandResult(
                  message.commandId(),
                  message.targetApplication(),
                  "chaos-poc-demo",
                  InstanceOutcome.SUCCESS,
                  null,
                  200,
                  Instant.now());
            });
    ChaosMonkeyStatusCache cache = mock(ChaosMonkeyStatusCache.class);
    ChaosCommandDispatcher dispatcher =
        new ChaosCommandDispatcher(
            client,
            store,
            Runnable::run,
            new ChaosCommandStatusService(store, new ChaosRelayProperties()),
            cache);
    UUID commandId = UUID.randomUUID();
    store.save(
        new CommandRecord(
            commandId,
            ChaosCommandAction.CONFIGURE_AND_ENABLE,
            "chaos-poc-demo",
            2,
            Instant.now(),
            "same-pod-name",
            "tester",
            Instant.now().plusSeconds(60),
            null,
            List.of()));

    dispatcher.dispatch(
        new ChaosCommandMessage(
            commandId,
            "test",
            "chaos-poc-demo",
            ChaosCommandAction.CONFIGURE_AND_ENABLE,
            null,
            Instant.now().plusSeconds(60),
            "tester",
            "same-pod-name"),
        List.of(
            new DefaultServiceInstance(
                "chaos-poc-demo-1", "chaos-poc-demo", "10.0.0.1", 8080, false),
            new DefaultServiceInstance(
                "chaos-poc-demo-2", "chaos-poc-demo", "10.0.0.2", 8080, false)));

    CommandRecord stored = store.findById(commandId).orElseThrow();
    assertThat(stored.instances())
        .extracting(instance -> instance.podName())
        .containsExactlyInAnyOrder("chaos-poc-demo-1", "chaos-poc-demo-2");
    assertThat(
            new ChaosCommandStatusService(store, new ChaosRelayProperties())
                .aggregateStatus(stored))
        .isEqualTo(CommandAggregateStatus.APPLIED);
    verify(cache, atLeastOnce()).refresh("chaos-poc-demo");
  }

  /** Store that replaces a result when the instance id matches, same as the database store. */
  private static final class RecordingStore implements ChaosCommandStore {

    private final ConcurrentHashMap<UUID, CommandRecord> commands = new ConcurrentHashMap<>();

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
      return Optional.empty();
    }

    @Override
    public List<CommandRecord> findByApplication(String applicationName, int limit) {
      return List.of();
    }

    @Override
    public List<CommandRecord> findByApplicationAndActions(
        String applicationName, Collection<ChaosCommandAction> actions) {
      return List.of();
    }

    @Override
    public Page<CommandRecord> findPage(
        Collection<String> applications, ChaosCommandAction action, Pageable pageable) {
      return Page.empty(pageable);
    }

    @Override
    public List<CommandRecord> findMatching(
        Collection<String> applications, ChaosCommandAction action) {
      return List.of();
    }

    @Override
    public void addResult(ChaosCommandResult result) {
      commands.computeIfPresent(
          result.commandId(),
          (id, record) -> {
            List<com.samba.chaos.relay.model.ChaosInstanceStatus> instances =
                new ArrayList<>(record.instances());
            instances.removeIf(instance -> instance.podName().equals(result.podName()));
            instances.add(
                new com.samba.chaos.relay.model.ChaosInstanceStatus(
                    result.podName(),
                    result.outcome(),
                    result.reportedAt(),
                    result.failedStep(),
                    result.httpStatus()));
            return new CommandRecord(
                record.commandId(),
                record.action(),
                record.targetApplication(),
                record.expectedInstances(),
                record.publishedAt(),
                record.correlationId(),
                record.issuedBy(),
                record.expiresAt(),
                record.assault(),
                instances,
                record.instanceSelection(),
                record.instanceIds());
          });
    }
  }
}
