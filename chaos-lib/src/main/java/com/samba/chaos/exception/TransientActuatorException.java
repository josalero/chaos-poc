package com.samba.chaos.exception;

/** Actuator failure that the retry policy may attempt again. */
public final class TransientActuatorException extends RuntimeException {

  private final String step;
  private final int httpStatus;

  /** Records the actuator step and HTTP status that failed. */
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
