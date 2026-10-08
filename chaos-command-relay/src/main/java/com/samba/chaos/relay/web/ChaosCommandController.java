package com.samba.chaos.relay.web;

import com.samba.chaos.command.ChaosCommandAction;
import com.samba.chaos.relay.model.ChaosCommandPageResponse;
import com.samba.chaos.relay.model.ChaosCommandRequest;
import com.samba.chaos.relay.model.ChaosCommandStatusResponse;
import com.samba.chaos.relay.model.CommandAggregateStatus;
import com.samba.chaos.relay.model.ValidationErrorResponse;
import com.samba.chaos.relay.model.ValidationErrorResponse.FieldError;
import com.samba.chaos.relay.service.ChaosCommandService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Submit a command and read its aggregate status.
 *
 * <pre>
 * POST /internal/v1/chaos/commands
 * { "environment": "test",
 *   "targetApplication": "chaos-poc-demo",
 *   "action": "DISABLE",
 *   "issuedBy": "chaos-console" }
 *
 * 202 { "status": "PUBLISHED", "expectedInstances": 2,
 *       "statusUrl": "/internal/v1/chaos/commands/{id}" }
 * </pre>
 *
 * <p>A validation failure is 400. No UP instances is 503 with status {@code NO_INSTANCES}. The 202
 * returns before fan-out finishes. Poll {@code statusUrl} until the aggregate is terminal.
 */
@RestController
@RequestMapping("/internal/v1/chaos/commands")
public class ChaosCommandController {

  private final ChaosCommandService commandService;

  /**
   * Creates the controller.
   *
   * @param commandService submit and status lookup
   */
  public ChaosCommandController(ChaosCommandService commandService) {
    this.commandService = commandService;
  }

  /**
   * Lists stored commands, newest first.
   *
   * <pre>
   * GET /internal/v1/chaos/commands?page=0&amp;size=50&amp;application=chaos-poc-demo
   * 200 { "content": [], "page": 0, "size": 50, "totalElements": 0 }
   * </pre>
   *
   * @param page zero-based page index
   * @param size page size, clamped to 1..200
   * @param application optional allowlisted name
   * @param status optional aggregate status
   * @param action optional command action
   * @return 200 with the page, or 400 when status or action is not a known value
   */
  @GetMapping
  public ResponseEntity<?> list(
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "50") int size,
      @RequestParam(name = "application", required = false) String application,
      @RequestParam(name = "status", required = false) String status,
      @RequestParam(name = "action", required = false) String action) {
    CommandAggregateStatus parsedStatus;
    try {
      parsedStatus = parseStatus(status);
    } catch (IllegalArgumentException ex) {
      return rejected("status", "unknown status");
    }
    ChaosCommandAction parsedAction;
    try {
      parsedAction = parseAction(action);
    } catch (IllegalArgumentException ex) {
      return rejected("action", "unknown action");
    }
    ChaosCommandPageResponse body =
        commandService.list(page, size, application, parsedStatus, parsedAction);
    return ResponseEntity.ok(body);
  }

  /**
   * Validates the body, stores the command, and fans out to the selected UP instances.
   *
   * @param request command to publish; {@code commandId} may be omitted
   * @return 202 when published, 400 when rejected, 503 when discovery has no UP instances
   */
  @PostMapping
  public ResponseEntity<?> submit(@Valid @RequestBody ChaosCommandRequest request) {
    return switch (commandService.submit(request)) {
      case ChaosCommandService.SubmitResult.Accepted accepted ->
          ResponseEntity.status(HttpStatus.ACCEPTED).body(accepted.response());
      case ChaosCommandService.SubmitResult.Rejected rejected ->
          ResponseEntity.badRequest().body(rejected.response());
      case ChaosCommandService.SubmitResult.Unavailable unavailable ->
          ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(unavailable.response());
    };
  }

  /**
   * Returns the aggregate computed on this read.
   *
   * <pre>
   * GET /internal/v1/chaos/commands/{commandId}
   * 200 { "status": "APPLIED", "successCount": 2, "expectedInstances": 2 }
   * </pre>
   *
   * @param commandId id returned by submit
   * @return 200 with the current aggregate, or 404 when the id is unknown
   */
  @GetMapping("/{commandId}")
  public ResponseEntity<ChaosCommandStatusResponse> getStatus(@PathVariable UUID commandId) {
    return commandService
        .getStatus(commandId)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  private CommandAggregateStatus parseStatus(String status) {
    if (status == null || status.isBlank()) {
      return null;
    }
    return CommandAggregateStatus.valueOf(status.trim());
  }

  private ChaosCommandAction parseAction(String action) {
    if (action == null || action.isBlank()) {
      return null;
    }
    return ChaosCommandAction.valueOf(action.trim());
  }

  private ResponseEntity<ValidationErrorResponse> rejected(String field, String message) {
    return ResponseEntity.badRequest()
        .body(new ValidationErrorResponse("REJECTED", List.of(new FieldError(field, message))));
  }
}
