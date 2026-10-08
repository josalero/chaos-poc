package com.samba.chaos.relay.store;

import com.samba.chaos.command.ChaosAssaultConfig;
import com.samba.chaos.command.ChaosCommandAction;
import com.samba.chaos.command.ChaosCommandResult;
import com.samba.chaos.relay.model.ChaosInstanceStatus;
import com.samba.chaos.relay.model.InstanceSelection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link ChaosCommandStore} backed by the relay database.
 *
 * <p>{@code environment} is stored as {@code test}. The validator rejects every other value before
 * a command is saved.
 */
@Service
public class JpaChaosCommandStore implements ChaosCommandStore {

  private static final String ENVIRONMENT = "test";
  private static final TypeReference<List<String>> INSTANCE_IDS = new TypeReference<>() {};

  private final ChaosCommandRepository repository;
  private final JsonMapper jsonMapper;

  /**
   * Creates the store.
   *
   * @param repository command rows
   * @param jsonMapper serializes assault JSON and instance ids
   */
  public JpaChaosCommandStore(ChaosCommandRepository repository, JsonMapper jsonMapper) {
    this.repository = repository;
    this.jsonMapper = jsonMapper;
  }

  @Override
  @Transactional
  public void save(CommandRecord record) {
    ChaosCommandEntity entity =
        repository.findById(record.commandId()).orElseGet(ChaosCommandEntity::new);
    entity.setCommandId(record.commandId());
    entity.setTargetApplication(record.targetApplication());
    entity.setAction(record.action());
    entity.setEnvironment(ENVIRONMENT);
    entity.setIssuedBy(record.issuedBy());
    entity.setCorrelationId(record.correlationId());
    entity.setExpiresAt(record.expiresAt());
    entity.setPublishedAt(record.publishedAt());
    entity.setExpectedInstances(record.expectedInstances());
    entity.setInstanceSelection(
        record.instanceSelection() == null ? InstanceSelection.ALL : record.instanceSelection());
    entity.setInstanceIdsJson(writeInstanceIds(record.instanceIds()));
    entity.setAssaultJson(writeAssault(record.assault()));
    entity.getResults().clear();
    repository.save(entity);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<CommandRecord> findById(UUID commandId) {
    return repository.findById(commandId).map(this::toRecord);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<CommandRecord> findLatestByApplication(String applicationName) {
    return repository
        .findFirstByTargetApplicationOrderByPublishedAtDesc(applicationName)
        .map(this::toRecord);
  }

  @Override
  @Transactional(readOnly = true)
  public List<CommandRecord> findByApplication(String applicationName, int limit) {
    int bounded = Math.max(limit, 1);
    return repository
        .findByTargetApplicationOrderByPublishedAtDesc(applicationName, PageRequest.of(0, bounded))
        .stream()
        .map(this::toRecord)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<CommandRecord> findByApplicationAndActions(
      String applicationName, Collection<ChaosCommandAction> actions) {
    return repository
        .findByTargetApplicationAndActionInOrderByPublishedAtDesc(applicationName, actions)
        .stream()
        .map(this::toRecord)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Page<CommandRecord> findPage(
      Collection<String> applications, ChaosCommandAction action, Pageable pageable) {
    if (applications == null || applications.isEmpty()) {
      return new PageImpl<>(List.of(), pageable, 0);
    }
    Page<UUID> ids =
        action == null
            ? repository.pageIdsByApplications(applications, pageable)
            : repository.pageIdsByApplicationsAndAction(applications, action, pageable);
    return new PageImpl<>(loadInOrder(ids.getContent()), pageable, ids.getTotalElements());
  }

  @Override
  @Transactional(readOnly = true)
  public List<CommandRecord> findMatching(
      Collection<String> applications, ChaosCommandAction action) {
    if (applications == null || applications.isEmpty()) {
      return List.of();
    }
    List<ChaosCommandEntity> entities =
        action == null
            ? repository.findByTargetApplicationInOrderByPublishedAtDesc(applications)
            : repository.findByTargetApplicationInAndActionOrderByPublishedAtDesc(
                applications, action);
    return entities.stream().map(this::toRecord).toList();
  }

  @Override
  @Transactional
  public void addResult(ChaosCommandResult result) {
    ChaosCommandEntity entity = repository.findById(result.commandId()).orElse(null);
    if (entity == null) {
      return;
    }
    entity.upsertResult(
        result.podName(),
        result.outcome(),
        result.reportedAt(),
        result.failedStep(),
        result.httpStatus());
    repository.save(entity);
  }

  private List<CommandRecord> loadInOrder(List<UUID> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    Map<UUID, ChaosCommandEntity> byId =
        repository.findByCommandIdIn(ids).stream()
            .collect(Collectors.toMap(ChaosCommandEntity::getCommandId, Function.identity()));
    List<CommandRecord> records = new ArrayList<>();
    for (UUID id : ids) {
      ChaosCommandEntity entity = byId.get(id);
      if (entity != null) {
        records.add(toRecord(entity));
      }
    }
    return records;
  }

  private CommandRecord toRecord(ChaosCommandEntity entity) {
    List<ChaosInstanceStatus> instances =
        entity.getResults().stream()
            .map(
                result ->
                    new ChaosInstanceStatus(
                        result.getInstanceId(),
                        result.getOutcome(),
                        result.getReportedAt(),
                        result.getFailedStep(),
                        result.getHttpStatus()))
            .toList();
    return new CommandRecord(
        entity.getCommandId(),
        entity.getAction(),
        entity.getTargetApplication(),
        entity.getExpectedInstances(),
        entity.getPublishedAt(),
        entity.getCorrelationId(),
        entity.getIssuedBy(),
        entity.getExpiresAt(),
        readAssault(entity.getAssaultJson(), entity.getCommandId()),
        instances,
        entity.getInstanceSelection(),
        readInstanceIds(entity.getInstanceIdsJson(), entity.getCommandId()));
  }

  private String writeAssault(ChaosAssaultConfig assault) {
    if (assault == null) {
      return null;
    }
    return jsonMapper.writeValueAsString(assault);
  }

  private String writeInstanceIds(List<String> instanceIds) {
    return jsonMapper.writeValueAsString(instanceIds == null ? List.of() : instanceIds);
  }

  private ChaosAssaultConfig readAssault(String json, UUID commandId) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return jsonMapper.readValue(json, ChaosAssaultConfig.class);
    } catch (JacksonException ex) {
      throw new IllegalStateException("Could not read stored assault for command " + commandId, ex);
    }
  }

  private List<String> readInstanceIds(String json, UUID commandId) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      List<String> ids = jsonMapper.readValue(json, INSTANCE_IDS);
      return ids == null ? List.of() : List.copyOf(ids);
    } catch (JacksonException ex) {
      throw new IllegalStateException(
          "Could not read stored instance ids for command " + commandId, ex);
    }
  }
}
