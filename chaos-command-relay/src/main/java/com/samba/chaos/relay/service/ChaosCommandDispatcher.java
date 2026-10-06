package com.samba.chaos.relay.service;

import com.samba.chaos.command.ChaosCommandMessage;
import com.samba.chaos.command.ChaosCommandResult;
import com.samba.chaos.command.InstanceOutcome;
import com.samba.chaos.relay.client.ChaosCommandClient;
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

  /**
   * Creates the dispatcher.
   *
   * @param commandClient Feign client that posts to a caller-supplied instance URI
   * @param commandStore where each instance result is appended
   * @param chaosDispatchExecutor virtual-thread executor, or the caller thread in tests
   */
  public ChaosCommandDispatcher(
      ChaosCommandClient commandClient,
      ChaosCommandStore commandStore,
      @Qualifier("chaosDispatchExecutor") TaskExecutor chaosDispatchExecutor) {
    this.commandClient = commandClient;
    this.commandStore = commandStore;
    this.chaosDispatchExecutor = chaosDispatchExecutor;
  }

  /**
   * Starts one task per instance. Returns when the tasks are queued, not when they finish.
   *
   * @param message command already assigned an id
   * @param instances UP instances captured at submit time
   */
  public void dispatch(ChaosCommandMessage message, List<ServiceInstance> instances) {
    instances.forEach(
        instance ->
            chaosDispatchExecutor.execute(() -> commandStore.addResult(send(message, instance))));
  }

  private ChaosCommandResult send(ChaosCommandMessage message, ServiceInstance instance) {
    try {
      ChaosCommandResult body = commandClient.apply(instance.getUri(), message);
      if (body != null) {
        return body;
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

  private static ChaosCommandResult outcome(
      ChaosCommandMessage message,
      ServiceInstance instance,
      InstanceOutcome outcome,
      Integer httpStatus) {
    String podName =
        instance.getInstanceId() != null ? instance.getInstanceId() : instance.getHost();
    return new ChaosCommandResult(
        message.commandId(),
        message.targetApplication(),
        podName,
        outcome,
        outcome.name().toLowerCase(),
        httpStatus,
        Instant.now());
  }
}
