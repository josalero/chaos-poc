package com.samba.chaos.relay;

import com.samba.chaos.relay.model.ChaosCommandStatusResponse;
import com.samba.chaos.relay.model.ChaosCommandSubmitResponse;
import com.samba.chaos.relay.model.ValidationErrorResponse;
import com.samba.chaos.relay.model.ValidationErrorResponse.FieldError;
import com.samba.chaos.relay.InMemoryChaosCommandStore.CommandRecord;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ChaosCommandService {

  private final ChaosCommandValidator validator;
  private final ExpectedInstancesResolver expectedInstancesResolver;
  private final ChaosCommandPublisher publisher;
  private final ChaosCommandStore commandStore;
  private final ChaosCommandStatusService statusService;

  public ChaosCommandService(
      ChaosCommandValidator validator,
      ExpectedInstancesResolver expectedInstancesResolver,
      ChaosCommandPublisher publisher,
      ChaosCommandStore commandStore,
      ChaosCommandStatusService statusService) {
    this.validator = validator;
    this.expectedInstancesResolver = expectedInstancesResolver;
    this.publisher = publisher;
    this.commandStore = commandStore;
    this.statusService = statusService;
  }

  public SubmitResult submit(ChaosCommandRequest request) {
    List<FieldError> errors = validator.validate(request);
    if (!errors.isEmpty()) {
      return SubmitResult.rejected(validator.toErrorResponse(errors));
    }

    UUID commandId = request.commandId() != null ? request.commandId() : UUID.randomUUID();
    Instant publishedAt = Instant.now();
    int expectedInstances = expectedInstancesResolver.resolve(request.targetApplication());

    CommandRecord record =
        new CommandRecord(
            commandId,
            request.action(),
            request.targetApplication(),
            expectedInstances,
            publishedAt,
            request.correlationId(),
            request.issuedBy(),
            request.expiresAt(),
            request.assault(),
            List.of());

    commandStore.save(record);
    publisher.publish(validator.toMessage(request, commandId));

    return SubmitResult.accepted(statusService.toSubmitResponse(record));
  }

  public Optional<ChaosCommandStatusResponse> getStatus(UUID commandId) {
    return commandStore.findById(commandId).map(statusService::toStatusResponse);
  }

  public sealed interface SubmitResult {
    record Accepted(ChaosCommandSubmitResponse response) implements SubmitResult {}

    record Rejected(ValidationErrorResponse response) implements SubmitResult {}

    static SubmitResult accepted(ChaosCommandSubmitResponse response) {
      return new Accepted(response);
    }

    static SubmitResult rejected(ValidationErrorResponse response) {
      return new Rejected(response);
    }
  }
}
