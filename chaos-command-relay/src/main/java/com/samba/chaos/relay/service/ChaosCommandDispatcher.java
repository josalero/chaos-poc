package com.samba.chaos.relay.service;

import com.samba.chaos.command.ChaosCommandMessage;
import com.samba.chaos.command.ChaosCommandResult;
import com.samba.chaos.command.InstanceOutcome;
import com.samba.chaos.relay.client.ChaosCommandClient;
import com.samba.chaos.relay.console.ChaosMonkeyStatusCache;
import com.samba.chaos.relay.model.CommandAggregateStatus;
import com.samba.chaos.relay.store.ChaosCommandStore;
import feign.FeignException;
import feign.RetryableException;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.core.task.TaskExecutor;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.stereotype.Component;

/**
 * Posts one command to each discovered instance on the dispatch executor.
 *
 * <p>A missing token or a connection failure is stored as {@code UNREACHABLE}. An HTTP error body
 * is stored as {@code REJECTED}. Other runtime failures are rethrown so the task can surface them.
 */
@Component
public class ChaosCommandDispatcher {

  private static final Logger log = LoggerFactory.getLogger(ChaosCommandDispatcher.class);

  private final ChaosCommandClient commandClient;
  private final ChaosCommandStore commandStore;
  private final TaskExecutor chaosDispatchExecutor;
  private final ChaosCommandStatusService statusService;
  private final ChaosMonkeyStatusCache statusCache;

  /**
   * Creates the dispatcher.
   *
   * @param commandClient Feign client that posts to a caller-supplied instance URI
   * @param commandStore where each instance result is appended
   * @param chaosDispatchExecutor virtual-thread executor, or the caller thread in tests
   * @param statusService aggregate used to decide when a command is finished
   * @param statusCache refreshed when a command reaches a terminal aggregate
   */
  public ChaosCommandDispatcher(
      ChaosCommandClient commandClient,
      ChaosCommandStore commandStore,
      @Qualifier("chaosDispatchExecutor") TaskExecutor chaosDispatchExecutor,
      ChaosCommandStatusService statusService,
      ChaosMonkeyStatusCache statusCache) {
    this.commandClient = commandClient;
    this.commandStore = commandStore;
    this.chaosDispatchExecutor = chaosDispatchExecutor;
    this.statusService = statusService;
    this.statusCache = statusCache;
  }

  /**
   * Starts one task per instance. Returns when the tasks are queued, not when they finish.
   *
   * @param message command already assigned an id
   * @param instances UP instances captured at submit time
   */
  public void dispatch(ChaosCommandMessage message, List<ServiceInstance> instances) {
    instances.forEach(
        instance -> chaosDispatchExecutor.execute(() -> record(send(message, instance))));
  }

  private void record(ChaosCommandResult result) {
    commandStore.addResult(result);
    commandStore
        .findById(result.commandId())
        .map(statusService::aggregateStatus)
        .filter(ChaosCommandDispatcher::isTerminal)
        .ifPresent(status -> statusCache.refresh(result.targetApplication()));
  }

  private static boolean isTerminal(CommandAggregateStatus status) {
    return status == CommandAggregateStatus.APPLIED
        || status == CommandAggregateStatus.FAILED
        || status == CommandAggregateStatus.TIMED_OUT;
  }

  private ChaosCommandResult send(ChaosCommandMessage message, ServiceInstance instance) {
    try {
      ChaosCommandResult body = commandClient.apply(instance.getUri(), message);
      if (body != null) {
        return keyedToInstance(body, instance);
      }
      return outcome(message, instance, InstanceOutcome.ACTUATOR_ERROR, null);
    } catch (RuntimeException ex) {
      OAuth2AuthorizationException tokenUnavailable = oauthFailure(ex);
      if (tokenUnavailable != null) {
        if (log.isErrorEnabled()) {
          log.error(
              "Chaos command {} not sent to instance {}: access token request failed (error={})",
              message.commandId(),
              instance.getInstanceId(),
              tokenUnavailable.getError().getErrorCode());
        }
        return outcome(message, instance, InstanceOutcome.UNREACHABLE, null);
      }
      if (ex instanceof RetryableException) {
        if (log.isWarnEnabled()) {
          log.warn(
              "Chaos command {} could not reach instance {}",
              message.commandId(),
              instance.getInstanceId());
        }
        return outcome(message, instance, InstanceOutcome.UNREACHABLE, null);
      }
      if (ex instanceof FeignException rejected) {
        Integer status = rejected.status() > 0 ? rejected.status() : null;
        return outcome(message, instance, InstanceOutcome.REJECTED, status);
      }
      throw ex;
    }
  }

  private static OAuth2AuthorizationException oauthFailure(Throwable failure) {
    Throwable current = failure;
    while (current != null) {
      if (current instanceof OAuth2AuthorizationException oauth) {
        return oauth;
      }
      current = current.getCause();
    }
    return null;
  }

  /**
   * Stores the result under the Eureka instance id. Pods of one service share {@code pod-name}, so
   * the body name would collapse two replicas into one result.
   *
   * @param body result returned by the instance
   * @param instance instance the relay posted to
   * @return the same result with {@code podName} set to the instance id
   */
  private static ChaosCommandResult keyedToInstance(
      ChaosCommandResult body, ServiceInstance instance) {
    return new ChaosCommandResult(
        body.commandId(),
        body.targetApplication(),
        instanceKey(instance),
        body.outcome(),
        body.failedStep(),
        body.httpStatus(),
        body.reportedAt());
  }

  private static ChaosCommandResult outcome(
      ChaosCommandMessage message,
      ServiceInstance instance,
      InstanceOutcome outcome,
      Integer httpStatus) {
    return new ChaosCommandResult(
        message.commandId(),
        message.targetApplication(),
        instanceKey(instance),
        outcome,
        outcome.name().toLowerCase(),
        httpStatus,
        Instant.now());
  }

  private static String instanceKey(ServiceInstance instance) {
    return instance.getInstanceId() != null ? instance.getInstanceId() : instance.getHost();
  }
}
