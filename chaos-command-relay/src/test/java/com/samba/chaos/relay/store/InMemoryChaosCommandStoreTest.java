package com.samba.chaos.relay.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.samba.chaos.command.ChaosCommandAction;
import com.samba.chaos.command.ChaosCommandResult;
import com.samba.chaos.command.InstanceOutcome;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryChaosCommandStoreTest {

  @Test
  void deletesOnlyRecordsOlderThanTtlCutoff() {
    InMemoryChaosCommandStore store = new InMemoryChaosCommandStore();
    UUID oldId = UUID.randomUUID();
    UUID recentId = UUID.randomUUID();
    store.save(record(oldId, Instant.now().minusSeconds(7200)));
    store.save(record(recentId, Instant.now()));

    int deleted = store.deletePublishedBefore(Instant.now().minusSeconds(3600));

    assertThat(deleted).isEqualTo(1);
    assertThat(store.findById(oldId)).isEmpty();
    assertThat(store.findById(recentId)).isPresent();
  }

  @Test
  void findsLatestAndReplacesResultForTheSamePod() {
    InMemoryChaosCommandStore store = new InMemoryChaosCommandStore();
    UUID older = UUID.randomUUID();
    UUID newer = UUID.randomUUID();
    store.save(record(older, Instant.now().minusSeconds(10), "orders"));
    store.save(record(newer, Instant.now(), "orders"));
    store.save(record(UUID.randomUUID(), Instant.now(), "billing"));

    assertThat(store.findLatestByApplication("orders"))
        .get()
        .extracting(CommandRecord::commandId)
        .isEqualTo(newer);
    assertThat(store.findByApplication("orders"))
        .extracting(CommandRecord::commandId)
        .containsExactly(newer, older);
    assertThat(store.findAll()).hasSize(3);

    store.addResult(
        new ChaosCommandResult(
            newer, "orders", "pod-a", InstanceOutcome.SUCCESS, null, 200, Instant.now()));
    store.addResult(
        new ChaosCommandResult(
            newer,
            "orders",
            "pod-a",
            InstanceOutcome.ACTUATOR_ERROR,
            "enable",
            500,
            Instant.now()));

    assertThat(store.findById(newer).orElseThrow().instances()).hasSize(1);
    assertThat(store.findById(newer).orElseThrow().failureCount()).isEqualTo(1);
    store.delete(newer);
    assertThat(store.findById(newer)).isEmpty();
  }

  private CommandRecord record(UUID id, Instant publishedAt) {
    return record(id, publishedAt, "orders");
  }

  private CommandRecord record(UUID id, Instant publishedAt, String applicationName) {
    return new CommandRecord(
        id,
        ChaosCommandAction.DISABLE,
        applicationName,
        1,
        publishedAt,
        null,
        "tester",
        null,
        null,
        List.of());
  }
}
