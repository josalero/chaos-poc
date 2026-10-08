package com.samba.chaos.relay.service;

import com.samba.chaos.command.ChaosAssaultConfig;
import com.samba.chaos.relay.config.ChaosRelayProperties;
import com.samba.chaos.relay.model.ChaosCatalogEntry;
import com.samba.chaos.relay.model.ChaosCatalogSaveRequest;
import com.samba.chaos.relay.store.ChaosCatalogEntity;
import com.samba.chaos.relay.store.ChaosCatalogRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Saved scenarios for one allowlisted service.
 *
 * <p>A repeated label replaces the stored action and assault and keeps the same id. Entries stay
 * until they are deleted.
 */
@Service
public class ChaosCatalogService {

  private final ChaosRelayProperties properties;
  private final ChaosCatalogRepository repository;
  private final JsonMapper jsonMapper;

  /**
   * Creates the catalog.
   *
   * @param properties allowlist
   * @param repository catalog rows
   * @param jsonMapper serializes the assault
   */
  public ChaosCatalogService(
      ChaosRelayProperties properties, ChaosCatalogRepository repository, JsonMapper jsonMapper) {
    this.properties = properties;
    this.repository = repository;
    this.jsonMapper = jsonMapper;
  }

  /**
   * Lists saved scenarios for one service.
   *
   * @param applicationName Eureka application name
   * @return empty when the name is not allowlisted
   */
  @Transactional(readOnly = true)
  public Optional<List<ChaosCatalogEntry>> list(String applicationName) {
    if (!allowlisted(applicationName)) {
      return Optional.empty();
    }
    return Optional.of(
        repository.findByTargetApplicationOrderByLabelAsc(applicationName).stream()
            .map(this::toEntry)
            .toList());
  }

  /**
   * Lists saved scenarios for the allowlist, or for one service.
   *
   * @param applicationName optional Eureka application name; blank lists every allowlisted service
   * @return matching rows, empty when the name is not allowlisted
   */
  @Transactional(readOnly = true)
  public List<ChaosCatalogEntry> listAll(String applicationName) {
    if (applicationName != null && !applicationName.isBlank()) {
      return list(applicationName).orElse(List.of());
    }
    List<String> allowlist = properties.getAllowedTargetApplications();
    if (allowlist.isEmpty()) {
      return List.of();
    }
    return repository
        .findByTargetApplicationInOrderByTargetApplicationAscLabelAsc(allowlist)
        .stream()
        .map(this::toEntry)
        .toList();
  }

  /**
   * Saves or replaces one label on a service.
   *
   * @param applicationName Eureka application name
   * @param request label, action, and assault
   * @return empty when the name is not allowlisted
   */
  @Transactional
  public Optional<ChaosCatalogEntry> save(String applicationName, ChaosCatalogSaveRequest request) {
    if (!allowlisted(applicationName)) {
      return Optional.empty();
    }
    ChaosCatalogEntity entity =
        repository
            .findByTargetApplicationAndLabel(applicationName, request.label().trim())
            .orElseGet(ChaosCatalogEntity::new);
    if (entity.getCatalogId() == null) {
      entity.setCatalogId(UUID.randomUUID());
      entity.setTargetApplication(applicationName);
      entity.setLabel(request.label().trim());
      entity.setCreatedAt(Instant.now());
    }
    entity.setAction(request.action());
    entity.setAssaultJson(writeAssault(request.assault()));
    return Optional.of(toEntry(repository.save(entity)));
  }

  /**
   * Deletes one entry that belongs to the service.
   *
   * @param applicationName Eureka application name
   * @param catalogId catalog id
   * @return empty when the name is not allowlisted; false when the id is not on that service
   */
  @Transactional
  public Optional<Boolean> delete(String applicationName, UUID catalogId) {
    if (!allowlisted(applicationName)) {
      return Optional.empty();
    }
    Optional<ChaosCatalogEntity> entity =
        repository.findByCatalogIdAndTargetApplication(catalogId, applicationName);
    if (entity.isEmpty()) {
      return Optional.of(false);
    }
    repository.delete(entity.get());
    return Optional.of(true);
  }

  private boolean allowlisted(String applicationName) {
    return properties.getAllowedTargetApplications().contains(applicationName);
  }

  private ChaosCatalogEntry toEntry(ChaosCatalogEntity entity) {
    return new ChaosCatalogEntry(
        entity.getCatalogId(),
        entity.getTargetApplication(),
        entity.getLabel(),
        entity.getAction(),
        readAssault(entity.getAssaultJson(), entity.getCatalogId()),
        entity.getCreatedAt());
  }

  private String writeAssault(ChaosAssaultConfig assault) {
    if (assault == null) {
      return null;
    }
    return jsonMapper.writeValueAsString(assault);
  }

  private ChaosAssaultConfig readAssault(String json, UUID catalogId) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return jsonMapper.readValue(json, ChaosAssaultConfig.class);
    } catch (JacksonException ex) {
      throw new IllegalStateException("Could not read stored assault for catalog " + catalogId, ex);
    }
  }
}
