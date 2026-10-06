package com.samba.chaos.web;

import com.samba.chaos.command.ChaosCommandMessage;
import com.samba.chaos.command.ChaosCommandResult;
import com.samba.chaos.command.InstanceOutcome;
import com.samba.chaos.config.ChaosSecurityConfiguration;
import com.samba.chaos.service.ChaosCommandApplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Receives a chaos command and returns the instance result. */
@RestController
@RequestMapping(ChaosCommandEndpoint.PATH)
public class ChaosCommandEndpoint {

  public static final String PATH = "/internal/chaos/commands";

  private final ChaosCommandApplier applier;

  ChaosCommandEndpoint(ChaosCommandApplier applier) {
    this.applier = applier;
  }

  /**
   * Applies the command when the caller holds {@code SCOPE_chaos.command}.
   *
   * @param message command to apply on this instance
   * @return 200 with the result, or 409 when the command was rejected
   */
  @PostMapping
  @PreAuthorize("hasAuthority('" + ChaosSecurityConfiguration.COMMAND_AUTHORITY + "')")
  public ResponseEntity<ChaosCommandResult> apply(@RequestBody ChaosCommandMessage message) {
    ChaosCommandResult result = applier.apply(message);
    HttpStatus status =
        result.outcome() == InstanceOutcome.REJECTED ? HttpStatus.CONFLICT : HttpStatus.OK;
    return ResponseEntity.status(status).body(result);
  }
}
