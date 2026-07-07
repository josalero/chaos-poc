package com.samba.chaos.relay.console;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samba.chaos.listener.message.ChaosCommandAction;
import com.samba.chaos.relay.model.CommandAggregateStatus;
import com.samba.chaos.relay.ChaosCommandStatusService;
import com.samba.chaos.relay.ChaosCommandStore;
import com.samba.chaos.relay.InMemoryChaosCommandStore.CommandRecord;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChaosConsoleServiceHistoryTest {

  @Mock private ChaosCommandStore commandStore;
  @Mock private ChaosCommandStatusService commandStatusService;

  private ChaosConsoleService consoleService;

  @BeforeEach
  void setUp() {
    consoleService =
        new ChaosConsoleService(
            null,
            null,
            null,
            null,
            commandStore,
            commandStatusService,
            null,
            null,
            new ObjectMapper());
  }

  @Test
  void listCommandHistory_returnsNewestFirstWithAggregateStatus() {
    UUID olderId = UUID.randomUUID();
    UUID newerId = UUID.randomUUID();
    Instant older = Instant.parse("2026-07-01T10:00:00Z");
    Instant newer = Instant.parse("2026-07-02T10:00:00Z");

    CommandRecord olderRecord =
        new CommandRecord(
            olderId,
            ChaosCommandAction.DISABLE,
            "chaos-poc-demo",
            1,
            older,
            null,
            "operator-a",
            null,
            null,
            List.of());
    CommandRecord newerRecord =
        new CommandRecord(
            newerId,
            ChaosCommandAction.CONFIGURE_AND_ENABLE,
            "chaos-poc-demo",
            1,
            newer,
            null,
            "operator-b",
            null,
            null,
            List.of());

    when(commandStore.findByApplication("chaos-poc-demo"))
        .thenReturn(List.of(newerRecord, olderRecord));
    when(commandStatusService.aggregateStatus(newerRecord))
        .thenReturn(CommandAggregateStatus.APPLIED);
    when(commandStatusService.aggregateStatus(olderRecord))
        .thenReturn(CommandAggregateStatus.FAILED);

    List<ChaosCommandHistoryEntry> history = consoleService.listCommandHistory("chaos-poc-demo");

    assertThat(history).hasSize(2);
    assertThat(history.get(0).commandId()).isEqualTo(newerId);
    assertThat(history.get(0).status()).isEqualTo(CommandAggregateStatus.APPLIED);
    assertThat(history.get(1).commandId()).isEqualTo(olderId);
    assertThat(history.get(1).issuedBy()).isEqualTo("operator-a");
  }
}
