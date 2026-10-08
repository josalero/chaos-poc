package com.samba.chaos.relay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samba.chaos.command.ChaosAssaultConfig;
import com.samba.chaos.command.ChaosCommandAction;
import com.samba.chaos.relay.config.ChaosRelayProperties;
import com.samba.chaos.relay.model.ChaosCatalogEntry;
import com.samba.chaos.relay.model.ChaosCatalogSaveRequest;
import com.samba.chaos.relay.store.ChaosCatalogEntity;
import com.samba.chaos.relay.store.ChaosCatalogRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ChaosCatalogServiceTest {

  @Autowired private ChaosCatalogService catalogService;
  @Autowired private ChaosCatalogRepository repository;
  @Autowired private ChaosRelayProperties properties;

  @Test
  void savesReplacesListsAndDeletesForOneService() {
    assertThat(catalogService.list("unknown")).isEmpty();
    assertThat(
            catalogService.save(
                "unknown",
                new ChaosCatalogSaveRequest("Latency", ChaosCommandAction.DISABLE, null)))
        .isEmpty();

    ChaosAssaultConfig assault = new ChaosAssaultConfig(1, true, false, true, 200, 800, null, null);
    ChaosCatalogEntry saved =
        catalogService
            .save(
                "chaos-poc-demo",
                new ChaosCatalogSaveRequest(
                    "Latency", ChaosCommandAction.CONFIGURE_AND_ENABLE, assault))
            .orElseThrow();
    ChaosCatalogEntry replaced =
        catalogService
            .save(
                "chaos-poc-demo",
                new ChaosCatalogSaveRequest(" Latency ", ChaosCommandAction.DISABLE, null))
            .orElseThrow();

    assertThat(replaced.catalogId()).isEqualTo(saved.catalogId());
    assertThat(replaced.action()).isEqualTo(ChaosCommandAction.DISABLE);
    assertThat(replaced.assault()).isNull();
    assertThat(replaced.targetApplication()).isEqualTo("chaos-poc-demo");
    assertThat(replaced.label()).isEqualTo("Latency");
    assertThat(replaced.createdAt()).isEqualTo(saved.createdAt());
    assertThat(catalogService.list("chaos-poc-demo").orElseThrow()).contains(replaced);
    assertThat(catalogService.delete("chaos-poc-demo", saved.catalogId())).contains(true);
    assertThat(catalogService.delete("chaos-poc-demo", saved.catalogId())).contains(false);
    assertThat(catalogService.delete("unknown", saved.catalogId())).isEmpty();
    assertThat(catalogService.list("chaos-poc-demo").orElseThrow()).isEmpty();
  }

  @Test
  void rejectsStoredAssaultThatIsNotJson() {
    ChaosCatalogEntry saved =
        catalogService
            .save(
                "chaos-poc-downstream",
                new ChaosCatalogSaveRequest("Broken", ChaosCommandAction.DISABLE, null))
            .orElseThrow();
    ChaosCatalogEntity entity = repository.findById(saved.catalogId()).orElseThrow();
    entity.setAssaultJson("{");
    repository.saveAndFlush(entity);

    try {
      assertThatThrownBy(() -> catalogService.list("chaos-poc-downstream"))
          .isInstanceOf(IllegalStateException.class);
    } finally {
      repository.deleteById(saved.catalogId());
    }
  }

  @Test
  void listsNothingWhenTheAllowlistIsEmpty() {
    List<String> saved = new ArrayList<>(properties.getAllowedTargetApplications());
    properties.getAllowedTargetApplications().clear();
    try {
      assertThat(catalogService.listAll(null)).isEmpty();
    } finally {
      properties.getAllowedTargetApplications().addAll(saved);
    }
  }

  @Test
  void listsEveryAllowlistedServiceAndOneService() {
    ChaosCatalogEntry demo =
        catalogService
            .save(
                "chaos-poc-demo",
                new ChaosCatalogSaveRequest("Plane demo", ChaosCommandAction.DISABLE, null))
            .orElseThrow();
    ChaosCatalogEntry downstream =
        catalogService
            .save(
                "chaos-poc-downstream",
                new ChaosCatalogSaveRequest("Plane downstream", ChaosCommandAction.DISABLE, null))
            .orElseThrow();
    try {
      assertThat(catalogService.listAll(null))
          .extracting(ChaosCatalogEntry::label)
          .contains("Plane demo", "Plane downstream");
      assertThat(catalogService.listAll("  "))
          .extracting(ChaosCatalogEntry::targetApplication)
          .contains("chaos-poc-demo", "chaos-poc-downstream");
      assertThat(catalogService.listAll("chaos-poc-demo"))
          .extracting(ChaosCatalogEntry::label)
          .contains("Plane demo");
      assertThat(catalogService.listAll("not-allowed")).isEmpty();
    } finally {
      catalogService.delete("chaos-poc-demo", demo.catalogId());
      catalogService.delete("chaos-poc-downstream", downstream.catalogId());
    }
  }
}
