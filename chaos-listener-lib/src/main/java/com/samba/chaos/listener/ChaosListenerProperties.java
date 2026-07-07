package com.samba.chaos.listener;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "samba.chaos.command-listener")
public class ChaosListenerProperties {

  private boolean enabled;
  private String environment = "test";
  private String applicationName;
  private String podName = "local";
  private String actuatorBaseUrl = "http://127.0.0.1:8080/actuator/chaosmonkey";
  private int maxApplyAttempts = 3;
  private long applyBackoffMs = 200;
  private int maxPublishAttempts = 3;
  private long publishBackoffMs = 100;
  private String resultsQueue = "chaos.command-results";
  private String commandsExchange = "chaos.commands.test";
  private RabbitMq rabbitmq = new RabbitMq();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getEnvironment() {
    return environment;
  }

  public void setEnvironment(String environment) {
    this.environment = environment;
  }

  public String getApplicationName() {
    return applicationName;
  }

  public void setApplicationName(String applicationName) {
    this.applicationName = applicationName;
  }

  public String getPodName() {
    return podName;
  }

  public void setPodName(String podName) {
    this.podName = podName;
  }

  public String getActuatorBaseUrl() {
    return actuatorBaseUrl;
  }

  public void setActuatorBaseUrl(String actuatorBaseUrl) {
    this.actuatorBaseUrl = actuatorBaseUrl;
  }

  public int getMaxApplyAttempts() {
    return maxApplyAttempts;
  }

  public void setMaxApplyAttempts(int maxApplyAttempts) {
    this.maxApplyAttempts = maxApplyAttempts;
  }

  public long getApplyBackoffMs() {
    return applyBackoffMs;
  }

  public void setApplyBackoffMs(long applyBackoffMs) {
    this.applyBackoffMs = applyBackoffMs;
  }

  public int getMaxPublishAttempts() {
    return maxPublishAttempts;
  }

  public void setMaxPublishAttempts(int maxPublishAttempts) {
    this.maxPublishAttempts = maxPublishAttempts;
  }

  public long getPublishBackoffMs() {
    return publishBackoffMs;
  }

  public void setPublishBackoffMs(long publishBackoffMs) {
    this.publishBackoffMs = publishBackoffMs;
  }

  public String getResultsQueue() {
    return resultsQueue;
  }

  public void setResultsQueue(String resultsQueue) {
    this.resultsQueue = resultsQueue;
  }

  public String getCommandsExchange() {
    return commandsExchange;
  }

  public void setCommandsExchange(String commandsExchange) {
    this.commandsExchange = commandsExchange;
  }

  public RabbitMq getRabbitmq() {
    return rabbitmq;
  }

  public void setRabbitmq(RabbitMq rabbitmq) {
    this.rabbitmq = rabbitmq;
  }

  public static class RabbitMq {

    private String host;
    private Integer port;
    private String username;
    private String password;

    public String getHost() {
      return host;
    }

    public void setHost(String host) {
      this.host = host;
    }

    public Integer getPort() {
      return port;
    }

    public void setPort(Integer port) {
      this.port = port;
    }

    public String getUsername() {
      return username;
    }

    public void setUsername(String username) {
      this.username = username;
    }

    public String getPassword() {
      return password;
    }

    public void setPassword(String password) {
      this.password = password;
    }
  }
}
