package com.samba.chaos.exception;

/** Actuator failure that must not be retried. */
public final class PermanentActuatorException extends RuntimeException {

  private final String step;
  private final int httpStatus;

  /** Records the actuator step, HTTP status, and cause of the failure. */
  public PermanentActuatorException(String step, int httpStatus, Throwable cause) {
    super("Permanent actuator failure step=" + step + " status=" + httpStatus, cause);
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
