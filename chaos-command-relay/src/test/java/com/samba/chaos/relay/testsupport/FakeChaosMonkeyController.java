package com.samba.chaos.relay.testsupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/actuator/chaosmonkey")
public class FakeChaosMonkeyController {

  private final AtomicBoolean enabled = new AtomicBoolean(false);
  private Map<String, Object> lastAssaultConfig = Map.of();
  private final List<String> auditLog = new ArrayList<>();

  @PostMapping("/assaults")
  public ResponseEntity<Void> configureAssaults(@RequestBody Map<String, Object> body) {
    lastAssaultConfig = body;
    auditLog.add("assaults");
    return ResponseEntity.ok().build();
  }

  @PostMapping("/enable")
  public ResponseEntity<Void> enable() {
    enabled.set(true);
    auditLog.add("enable");
    return ResponseEntity.ok().build();
  }

  @PostMapping("/disable")
  public ResponseEntity<Void> disable() {
    enabled.set(false);
    auditLog.add("disable");
    return ResponseEntity.ok().build();
  }

  @GetMapping("/status")
  public Map<String, Object> status() {
    return Map.of("enabled", enabled.get(), "lastAssaultConfig", lastAssaultConfig);
  }

  @GetMapping("/assaults")
  public Map<String, Object> assaults() {
    return lastAssaultConfig.isEmpty() ? Map.of() : lastAssaultConfig;
  }
}
