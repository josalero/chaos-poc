package com.samba.chaos.relay.store;

import com.samba.chaos.command.ChaosCommandAction;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data access for {@link ChaosCommandEntity}. */
public interface ChaosCommandRepository extends JpaRepository<ChaosCommandEntity, UUID> {

  /**
   * Newest command for one application.
   *
   * @param targetApplication Eureka application name
   * @return empty when that application has no commands
   */
  @EntityGraph(attributePaths = "results")
  Optional<ChaosCommandEntity> findFirstByTargetApplicationOrderByPublishedAtDesc(
      String targetApplication);

  /**
   * Commands for one application, newest first, limited by the page size.
   *
   * @param targetApplication Eureka application name
   * @param pageable page size is the history limit
   * @return matching commands
   */
  @EntityGraph(attributePaths = "results")
  List<ChaosCommandEntity> findByTargetApplicationOrderByPublishedAtDesc(
      String targetApplication, Pageable pageable);

  /**
   * Commands whose action is one of {@code actions}, newest first.
   *
   * @param targetApplication Eureka application name
   * @param actions actions to include
   * @return matching commands
   */
  @EntityGraph(attributePaths = "results")
  List<ChaosCommandEntity> findByTargetApplicationAndActionInOrderByPublishedAtDesc(
      String targetApplication, Collection<ChaosCommandAction> actions);

  /**
   * Command ids for the named applications, newest first.
   *
   * @param applications Eureka application names
   * @param pageable page index and size; do not add a sort
   * @return one page of ids
   */
  @Query(
      value =
          "select c.commandId from ChaosCommandEntity c"
              + " where c.targetApplication in :applications"
              + " order by c.publishedAt desc",
      countQuery =
          "select count(c) from ChaosCommandEntity c"
              + " where c.targetApplication in :applications")
  Page<UUID> pageIdsByApplications(
      @Param("applications") Collection<String> applications, Pageable pageable);

  /**
   * Command ids for one action on the named applications, newest first.
   *
   * @param applications Eureka application names
   * @param action command action
   * @param pageable page index and size; do not add a sort
   * @return one page of ids
   */
  @Query(
      value =
          "select c.commandId from ChaosCommandEntity c"
              + " where c.targetApplication in :applications and c.action = :action"
              + " order by c.publishedAt desc",
      countQuery =
          "select count(c) from ChaosCommandEntity c"
              + " where c.targetApplication in :applications and c.action = :action")
  Page<UUID> pageIdsByApplicationsAndAction(
      @Param("applications") Collection<String> applications,
      @Param("action") ChaosCommandAction action,
      Pageable pageable);

  /**
   * Commands whose id is in {@code commandIds}, with instance results.
   *
   * @param commandIds page of command ids
   * @return matching commands, unordered
   */
  @EntityGraph(attributePaths = "results")
  List<ChaosCommandEntity> findByCommandIdIn(Collection<UUID> commandIds);

  /**
   * Every command for the named applications, newest first, with instance results.
   *
   * @param applications Eureka application names
   * @return matching commands
   */
  @EntityGraph(attributePaths = "results")
  List<ChaosCommandEntity> findByTargetApplicationInOrderByPublishedAtDesc(
      Collection<String> applications);

  /**
   * Every command for one action on the named applications, newest first.
   *
   * @param applications Eureka application names
   * @param action command action
   * @return matching commands
   */
  @EntityGraph(attributePaths = "results")
  List<ChaosCommandEntity> findByTargetApplicationInAndActionOrderByPublishedAtDesc(
      Collection<String> applications, ChaosCommandAction action);
}
