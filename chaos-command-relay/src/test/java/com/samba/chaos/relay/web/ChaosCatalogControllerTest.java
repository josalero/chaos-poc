package com.samba.chaos.relay.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.samba.chaos.relay.model.ChaosCatalogEntry;
import com.samba.chaos.relay.service.ChaosCatalogService;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChaosCatalogControllerTest {

  @Test
  void listsTheCatalogForTheAllowlistOrOneService() {
    ChaosCatalogService catalogService = mock(ChaosCatalogService.class);
    ChaosCatalogEntry entry = mock(ChaosCatalogEntry.class);
    when(catalogService.listAll(null)).thenReturn(List.of(entry));
    when(catalogService.listAll("orders")).thenReturn(List.of());
    ChaosCatalogController controller = new ChaosCatalogController(catalogService);

    assertThat(controller.list(null)).containsExactly(entry);
    assertThat(controller.list("orders")).isEmpty();
  }
}
