package com.samba.chaos.relay;

import com.samba.chaos.listener.message.ChaosCommandResult;
import com.samba.chaos.relay.InMemoryChaosCommandStore.CommandRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChaosCommandStore {

  void save(CommandRecord record);

  Optional<CommandRecord> findById(UUID commandId);

  Optional<CommandRecord> findLatestByApplication(String applicationName);

  List<CommandRecord> findByApplication(String applicationName);

  List<CommandRecord> findAll();

  void addResult(ChaosCommandResult result);
}
