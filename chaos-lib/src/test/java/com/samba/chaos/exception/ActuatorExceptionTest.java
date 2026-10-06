package com.samba.chaos.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ActuatorExceptionTest {

  @Test
  void permanentFailureKeepsStepStatusAndCause() {
    IllegalStateException cause = new IllegalStateException("rejected");
    PermanentActuatorException failure = new PermanentActuatorException("assaults", 400, cause);

    assertThat(failure.getStep()).isEqualTo("assaults");
    assertThat(failure.getHttpStatus()).isEqualTo(400);
    assertThat(failure.getCause()).isSameAs(cause);
  }

  @Test
  void transientFailureKeepsStepStatusAndCause() {
    IllegalStateException cause = new IllegalStateException("unavailable");
    TransientActuatorException failure = new TransientActuatorException("enable", 503, cause);

    assertThat(failure.getStep()).isEqualTo("enable");
    assertThat(failure.getHttpStatus()).isEqualTo(503);
    assertThat(failure.getCause()).isSameAs(cause);
  }
}
