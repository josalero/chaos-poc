package com.samba.chaos.listener.exception;

public final class PermanentActuatorException extends RuntimeException {

  private final String step;
  private final int httpStatus;

  public PermanentActuatorException(String step, int httpStatus) {
    super("Permanent actuator failure step=" + step + " status=" + httpStatus);
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
