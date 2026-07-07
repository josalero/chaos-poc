package com.samba.chaos.relay;

import com.samba.chaos.relay.model.ChaosCommandStatusResponse;
import com.samba.chaos.relay.model.ChaosCommandSubmitResponse;
import com.samba.chaos.relay.model.ValidationErrorResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/chaos/commands")
public class ChaosCommandController {

  private final ChaosCommandService commandService;

  public ChaosCommandController(ChaosCommandService commandService) {
    this.commandService = commandService;
  }

  @PostMapping
  public ResponseEntity<?> submit(@Valid @RequestBody ChaosCommandRequest request) {
    ChaosCommandService.SubmitResult result = commandService.submit(request);
    if (result instanceof ChaosCommandService.SubmitResult.Accepted accepted) {
      return ResponseEntity.status(HttpStatus.ACCEPTED).body(accepted.response());
    }
    if (result instanceof ChaosCommandService.SubmitResult.Rejected rejected) {
      return ResponseEntity.badRequest().body(rejected.response());
    }
    throw new IllegalStateException("Unknown submit result: " + result);
  }

  @GetMapping("/{commandId}")
  public ResponseEntity<ChaosCommandStatusResponse> getStatus(@PathVariable UUID commandId) {
    return commandService
        .getStatus(commandId)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}
