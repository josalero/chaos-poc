package com.samba.chaos.relay.store;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access for {@link ChaosCatalogEntity}. */
public interface ChaosCatalogRepository extends JpaRepository<ChaosCatalogEntity, UUID> {

  /**
   * Saved scenarios for one service, alphabetical by label.
   *
   * @param targetApplication Eureka application name
   * @return matching catalog rows
   */
  List<ChaosCatalogEntity> findByTargetApplicationOrderByLabelAsc(String targetApplication);

  /**
   * Saved scenarios for the named services, ordered by service then label.
   *
   * @param targetApplications allowlisted Eureka application names
   * @return matching catalog rows
   */
  List<ChaosCatalogEntity> findByTargetApplicationInOrderByTargetApplicationAscLabelAsc(
      Collection<String> targetApplications);

  /**
   * One label on one service.
   *
   * @param targetApplication Eureka application name
   * @param label operator-facing name
   * @return empty when that label is not saved
   */
  Optional<ChaosCatalogEntity> findByTargetApplicationAndLabel(
      String targetApplication, String label);

  /**
   * One catalog row that belongs to the named service.
   *
   * @param catalogId catalog id
   * @param targetApplication Eureka application name
   * @return empty when the id is missing or belongs to another service
   */
  Optional<ChaosCatalogEntity> findByCatalogIdAndTargetApplication(
      UUID catalogId, String targetApplication);
}
