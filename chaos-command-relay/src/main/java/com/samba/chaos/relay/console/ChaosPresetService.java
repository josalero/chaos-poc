package com.samba.chaos.relay.console;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

@Service
public class ChaosPresetService {

  private final ObjectMapper objectMapper;
  private List<ChaosPreset> presets = List.of();

  public ChaosPresetService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    reload();
  }

  public List<ChaosPreset> listPresets() {
    return presets;
  }

  public Optional<ChaosPreset> findById(String id) {
    return presets.stream().filter(preset -> preset.id().equals(id)).findFirst();
  }

  public Optional<JsonNode> presetAssault(String presetId) {
    return findById(presetId)
        .map(
            preset -> {
              try {
                return objectMapper.readTree(preset.json()).path("assault");
              } catch (IOException ex) {
                throw new IllegalStateException("Invalid preset JSON: " + preset.id(), ex);
              }
            });
  }

  private void reload() {
    try {
      PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
      Resource[] resources = resolver.getResources("classpath:chaos/presets/*.json");
      List<ChaosPreset> loaded = new ArrayList<>();

      for (Resource resource : resources) {
        String filename = resource.getFilename();
        if (filename == null) {
          continue;
        }
        String id = filename.replace(".json", "");
        String json = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        loaded.add(new ChaosPreset(id, toLabel(id), json));
      }

      loaded.sort(Comparator.comparing(ChaosPreset::label));
      this.presets = List.copyOf(loaded);
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to load chaos presets", ex);
    }
  }

  private static String toLabel(String id) {
    if (id.startsWith("downstream-")) {
      return "(Downstream) " + id.substring("downstream-".length()).replace('-', ' ');
    }
    return id.replace('-', ' ') + " (DSUI reference)";
  }
}
