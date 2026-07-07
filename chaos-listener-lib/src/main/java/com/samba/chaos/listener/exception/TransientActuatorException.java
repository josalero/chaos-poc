package com.samba.chaos.listener.exception;

public final class TransientActuatorException extends RuntimeException {

  private final String step;
  private final int httpStatus;

  public TransientActuatorException(String step, int httpStatus, Throwable cause) {
    super("Transient actuator failure step=" + step + " status=" + httpStatus, cause);
    this.step = step;
    this.httpStatus = httpStatus;
  }

  public String getStep() {
    return step;
  }

  public int getHttpStatus() {
    return httpStatus;
  }
}
