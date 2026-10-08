package com.samba.chaos.relay.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samba.chaos.command.ChaosAssaultConfig;
import com.samba.chaos.command.ChaosCommandAction;
import com.samba.chaos.command.ChaosCommandResult;
import com.samba.chaos.command.InstanceOutcome;
import com.samba.chaos.relay.ChaosCommandRelayApplication;
import com.samba.chaos.relay.model.CommandAggregateStatus;
import com.samba.chaos.relay.model.InstanceSelection;
import com.samba.chaos.relay.service.ChaosCommandStatusService;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
class JpaChaosCommandStoreTest {

  @Autowired private ChaosCommandStore store;
  @Autowired private ChaosCommandRepository repository;
  @Autowired private ChaosCommandStatusService statusService;

  @Test
  void savesFindsLatestAndLimitsHistory() {
    UUID older = UUID.randomUUID();
    UUID newer = UUID.randomUUID();
    store.save(record(older, Instant.parse("2026-10-01T00:00:00Z"), "orders-limit"));
    store.save(record(newer, Instant.parse("2026-10-02T00:00:00Z"), "orders-limit"));
    store.save(record(UUID.randomUUID(), Instant.parse("2026-10-03T00:00:00Z"), "billing-limit"));

    assertThat(store.findLatestByApplication("orders-limit"))
        .get()
        .extracting(CommandRecord::commandId)
        .isEqualTo(newer);
    assertThat(store.findByApplication("orders-limit", 1))
        .extracting(CommandRecord::commandId)
        .containsExactly(newer);
    assertThat(store.findById(older)).isPresent();
    assertThat(
            store.findByApplicationAndActions("orders-limit", List.of(ChaosCommandAction.DISABLE)))
        .hasSize(2);
  }

  @Test
  void pagesCommandsNewestFirstAndFiltersAction() {
    UUID older = UUID.randomUUID();
    UUID newest = UUID.randomUUID();
    UUID configured = UUID.randomUUID();
    store.save(record(older, Instant.parse("2026-10-01T00:00:00Z"), "page-app"));
    store.save(record(newest, Instant.parse("2026-10-03T00:00:00Z"), "page-app"));
    store.save(
        record(
            configured,
            Instant.parse("2026-10-02T00:00:00Z"),
            "page-app",
            ChaosCommandAction.CONFIGURE));

    Page<CommandRecord> first = store.findPage(List.of("page-app"), null, PageRequest.of(0, 1));
    Page<CommandRecord> disabled =
        store.findPage(List.of("page-app"), ChaosCommandAction.DISABLE, PageRequest.of(0, 10));
    List<CommandRecord> matching =
        store.findMatching(List.of("page-app"), ChaosCommandAction.CONFIGURE);

    assertThat(first.getTotalElements()).isEqualTo(3);
    assertThat(first.getContent()).extracting(CommandRecord::commandId).containsExactly(newest);
    assertThat(disabled.getTotalElements()).isEqualTo(2);
    assertThat(matching).extracting(CommandRecord::commandId).containsExactly(configured);
    assertThat(store.findPage(List.of(), null, PageRequest.of(0, 10)).getTotalElements()).isZero();
    assertThat(store.findMatching(List.of(), null)).isEmpty();
  }

  @Test
  void replacesRepeatedInstanceResult() {
    UUID commandId = UUID.randomUUID();
    store.save(record(commandId, Instant.parse("2026-10-04T00:00:00Z"), "orders-upsert"));

    store.addResult(result(commandId, "orders-upsert", "pod-a", InstanceOutcome.SUCCESS));
    store.addResult(result(commandId, "orders-upsert", "pod-a", InstanceOutcome.ACTUATOR_ERROR));

    CommandRecord stored = store.findById(commandId).orElseThrow();
    assertThat(stored.instances()).hasSize(1);
    assertThat(stored.failureCount()).isEqualTo(1);
  }

  @Test
  void keepsParallelResultsForTwoInstances() throws Exception {
    UUID commandId = UUID.randomUUID();
    store.save(record(commandId, Instant.parse("2026-10-05T00:00:00Z"), "orders-parallel"));
    CountDownLatch start = new CountDownLatch(1);
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      executor.submit(report(start, commandId, "orders-parallel", "pod-1"));
      executor.submit(report(start, commandId, "orders-parallel", "pod-2"));
      start.countDown();
    }

    CommandRecord stored = store.findById(commandId).orElseThrow();
    assertThat(stored.instances())
        .extracting(instance -> instance.podName())
        .containsExactlyInAnyOrder("pod-1", "pod-2");
    assertThat(statusService.aggregateStatus(stored)).isEqualTo(CommandAggregateStatus.APPLIED);
  }

  @Test
  void historySurvivesNewApplicationContext(@TempDir Path directory) {
    String url = "jdbc:h2:file:" + directory.resolve("chaos-relay");
    UUID commandId = UUID.randomUUID();
    try (ConfigurableApplicationContext first = context(url)) {
      first
          .getBean(ChaosCommandStore.class)
          .save(record(commandId, Instant.parse("2026-10-06T00:00:00Z"), "orders-restart"));
    }

    try (ConfigurableApplicationContext second = context(url)) {
      assertThat(second.getBean(ChaosCommandStore.class).findById(commandId)).isPresent();
    }
  }

  @Test
  void ignoresResultForUnknownCommandAndStoresNullSelection() {
    store.addResult(result(UUID.randomUUID(), "orders-missing", "pod-a", InstanceOutcome.SUCCESS));
    UUID id = UUID.randomUUID();
    store.save(
        new CommandRecord(
            id,
            ChaosCommandAction.DISABLE,
            "orders-null",
            1,
            Instant.parse("2026-10-04T00:00:00Z"),
            null,
            "tester",
            null,
            null,
            List.of(),
            null,
            null));

    CommandRecord loaded = store.findById(id).orElseThrow();
    assertThat(loaded.instanceSelection()).isEqualTo(InstanceSelection.ALL);
    assertThat(loaded.instanceIds()).isEmpty();
    assertThat(loaded.assault()).isNull();
    assertThat(store.findByApplication("orders-null", 0)).hasSize(1);
  }

  @Test
  void rejectsStoredJsonThatCannotBeRead() {
    UUID id = UUID.randomUUID();
    store.save(record(id, Instant.parse("2026-10-05T00:00:00Z"), "orders-bad-json"));
    ChaosCommandEntity entity = repository.findById(id).orElseThrow();
    entity.setAssaultJson("{");
    repository.saveAndFlush(entity);
    assertThatThrownBy(() -> store.findById(id)).isInstanceOf(IllegalStateException.class);

    entity.setAssaultJson(null);
    entity.setInstanceIdsJson("{");
    repository.saveAndFlush(entity);
    assertThatThrownBy(() -> store.findById(id)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  @Transactional
  void readsResultIdentity() {
    UUID id = UUID.randomUUID();
    store.save(record(id, Instant.parse("2026-10-06T00:00:00Z"), "orders-row"));
    store.addResult(result(id, "orders-row", "pod-a", InstanceOutcome.SUCCESS));

    ChaosCommandResultEntity row = repository.findById(id).orElseThrow().getResults().getFirst();
    assertThat(row.getId()).isNotNull();
    assertThat(row.getCommand().getCommandId()).isEqualTo(id);
  }

  private Runnable report(CountDownLatch start, UUID commandId, String application, String pod) {
    return () -> {
      try {
        start.await();
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("interrupted", ex);
      }
      store.addResult(result(commandId, application, pod, InstanceOutcome.SUCCESS));
    };
  }

  private static ConfigurableApplicationContext context(String url) {
    return SpringApplication.run(
        ChaosCommandRelayApplication.class,
        "--spring.datasource.url=" + url,
        "--server.port=0",
        "--chaos.relay.status-refresh-interval-ms=3600000");
  }

  private static ChaosCommandResult result(
      UUID commandId, String application, String pod, InstanceOutcome outcome) {
    return new ChaosCommandResult(commandId, application, pod, outcome, null, 200, Instant.now());
  }

  private static CommandRecord record(UUID id, Instant publishedAt, String applicationName) {
    return record(id, publishedAt, applicationName, ChaosCommandAction.DISABLE);
  }

  private static CommandRecord record(
      UUID id, Instant publishedAt, String applicationName, ChaosCommandAction action) {
    return new CommandRecord(
        id,
        action,
        applicationName,
        2,
        publishedAt,
        "corr",
        "tester",
        null,
        new ChaosAssaultConfig(1, true, false, true, 10, 20, List.of("com.example.Order"), null),
        List.of(),
        InstanceSelection.ALL,
        List.of());
  }
}
