package com.samba.chaos.relay.service;

import com.samba.chaos.relay.model.ChaosCommandRequest;
import com.samba.chaos.relay.model.ChaosCommandStatusResponse;
import com.samba.chaos.relay.model.ChaosCommandSubmitResponse;
import com.samba.chaos.relay.model.InstanceSelection;
import com.samba.chaos.relay.model.ValidationErrorResponse;
import com.samba.chaos.relay.model.ValidationErrorResponse.FieldError;
import com.samba.chaos.relay.store.ChaosCommandStore;
import com.samba.chaos.relay.store.CommandRecord;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.stereotype.Service;

/**
 * Validates a command, stores it, and starts fan-out.
 *
 * <p>An empty discovery result does not store a command. A stored command is visible as {@code
 * PUBLISHED} before any instance reports.
 */
@Service
public class ChaosCommandService {

  private final ChaosCommandValidator validator;
  private final TargetInstancesResolver instancesResolver;
  private final ChaosCommandDispatcher dispatcher;
  private final ChaosCommandStore commandStore;
  private final ChaosCommandStatusService statusService;

  /**
   * Creates the submit service.
   *
   * @param validator field rules for environment, allowlist, assault, and expiry
   * @param instancesResolver UP instances from Eureka
   * @param dispatcher per-instance HTTP fan-out
   * @param commandStore in-memory command history
   * @param statusService aggregate and response mapping
   */
  public ChaosCommandService(
      ChaosCommandValidator validator,
      TargetInstancesResolver instancesResolver,
      ChaosCommandDispatcher dispatcher,
      ChaosCommandStore commandStore,
      ChaosCommandStatusService statusService) {
    this.validator = validator;
    this.instancesResolver = instancesResolver;
    this.dispatcher = dispatcher;
    this.commandStore = commandStore;
    this.statusService = statusService;
  }

  /**
   * Publishes one command to the selected UP instances of {@code targetApplication}.
   *
   * <pre>
   * submit(disable chaos-poc-demo)          -&gt; Accepted, expectedInstances = 2
   * submit(SOME, instanceIds = [pod-a])     -&gt; Accepted, expectedInstances = 1
   * submit(SOME, instanceIds = [missing])   -&gt; Rejected, field instanceIds
   * submit(target not on the allowlist)     -&gt; Rejected, field targetApplication
   * submit(allowlisted app with no UP instances)
   *   -&gt; Unavailable, status NO_INSTANCES
   * </pre>
   *
   * @param request command body; a null {@code commandId} is generated
   * @return accepted, rejected, or unavailable
   */
  public SubmitResult submit(ChaosCommandRequest request) {
    List<FieldError> errors = validator.validate(request);
    if (!errors.isEmpty()) {
      return SubmitResult.rejected(validator.toErrorResponse(errors));
    }

    List<ServiceInstance> up = instancesResolver.resolveUp(request.targetApplication());
    if (up.isEmpty()) {
      return SubmitResult.unavailable(
          new ValidationErrorResponse(
              "NO_INSTANCES",
              List.of(
                  new FieldError("targetApplication", "no UP instances registered in discovery"))));
    }

    List<ServiceInstance> targets = selectTargets(request, up);
    if (targets == null) {
      return SubmitResult.rejected(
          new ValidationErrorResponse(
              "REJECTED",
              List.of(
                  new FieldError(
                      "instanceIds", "one or more instance ids are not UP in discovery"))));
    }

    UUID commandId = request.commandId() != null ? request.commandId() : UUID.randomUUID();
    CommandRecord record = newRecord(request, commandId, targets.size());
    commandStore.save(record);
    dispatcher.dispatch(validator.toMessage(request, commandId), targets);
    return SubmitResult.accepted(statusService.toSubmitResponse(record));
  }

  /**
   * UP instances that should receive the command. Null means a SOME id is not UP.
   *
   * @param request accepted command
   * @param up current UP instances
   * @return selected instances, or null when a requested id is missing
   */
  private static List<ServiceInstance> selectTargets(
      ChaosCommandRequest request, List<ServiceInstance> up) {
    if (request.instanceSelection() != InstanceSelection.SOME) {
      return up;
    }

    Map<String, ServiceInstance> byId = new LinkedHashMap<>();
    for (ServiceInstance instance : up) {
      byId.put(instance.getInstanceId(), instance);
    }
    List<ServiceInstance> selected = new ArrayList<>();
    for (String instanceId : request.instanceIds()) {
      ServiceInstance match = byId.get(instanceId);
      if (match == null) {
        return null;
      }
      selected.add(match);
    }
    return selected;
  }

  private static CommandRecord newRecord(
      ChaosCommandRequest request, UUID commandId, int expectedInstances) {
    InstanceSelection selection =
        request.instanceSelection() == null ? InstanceSelection.ALL : request.instanceSelection();
    List<String> instanceIds =
        selection == InstanceSelection.SOME ? List.copyOf(request.instanceIds()) : List.of();
    return new CommandRecord(
        commandId,
        request.action(),
        request.targetApplication(),
        expectedInstances,
        Instant.now(),
        request.correlationId(),
        request.issuedBy(),
        request.expiresAt(),
        request.assault(),
        List.of(),
        selection,
        instanceIds);
  }

  /**
   * Reads one command and computes its aggregate at call time.
   *
   * @param commandId stored id
   * @return empty when the id is unknown or has been cleaned up
   */
  public Optional<ChaosCommandStatusResponse> getStatus(UUID commandId) {
    return commandStore.findById(commandId).map(statusService::toStatusResponse);
  }

  /** Outcome of {@link #submit(ChaosCommandRequest)} before HTTP mapping. */
  public sealed interface SubmitResult {
    /** The command was stored and fan-out started. Maps to HTTP 202. */
    record Accepted(ChaosCommandSubmitResponse response) implements SubmitResult {}

    /** Field validation failed. Maps to HTTP 400. */
    record Rejected(ValidationErrorResponse response) implements SubmitResult {}

    /** Discovery returned no UP instances. Maps to HTTP 503. */
    record Unavailable(ValidationErrorResponse response) implements SubmitResult {}

    /**
     * Returns the published outcome.
     *
     * @param response submit body
     * @return accepted result
     */
    static SubmitResult accepted(ChaosCommandSubmitResponse response) {
      return new Accepted(response);
    }

    /**
     * Returns the validation outcome.
     *
     * @param response field errors
     * @return rejected result
     */
    static SubmitResult rejected(ValidationErrorResponse response) {
      return new Rejected(response);
    }

    /**
     * Returns the empty-registry outcome.
     *
     * @param response error body whose status is {@code NO_INSTANCES}
     * @return unavailable result
     */
    static SubmitResult unavailable(ValidationErrorResponse response) {
      return new Unavailable(response);
    }
  }
}
