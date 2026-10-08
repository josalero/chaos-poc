package com.samba.chaos.relay.web;

import com.samba.chaos.relay.model.ChaosCatalogEntry;
import com.samba.chaos.relay.service.ChaosCatalogService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Saved assaults across the allowlist.
 *
 * <pre>
 * GET /internal/v1/chaos/catalog
 * GET /internal/v1/chaos/catalog?application=chaos-poc-demo
 * </pre>
 */
@RestController
@RequestMapping("/internal/v1/chaos/catalog")
public class ChaosCatalogController {

  private final ChaosCatalogService catalogService;

  /**
   * Creates the controller.
   *
   * @param catalogService catalog reads
   */
  public ChaosCatalogController(ChaosCatalogService catalogService) {
    this.catalogService = catalogService;
  }

  /**
   * Lists saved assaults, ordered by service then label.
   *
   * @param application optional allowlisted name; omitted lists every allowlisted service
   * @return matching rows, empty when the name is not allowlisted
   */
  @GetMapping
  public List<ChaosCatalogEntry> list(
      @RequestParam(name = "application", required = false) String application) {
    return catalogService.listAll(application);
  }
}
