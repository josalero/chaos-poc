package com.samba.chaos.relay;

import static org.assertj.core.api.Assertions.assertThat;

import com.samba.chaos.listener.message.ChaosCommandAction;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChaosCommandValidatorTest {

  private ChaosCommandValidator validator;

  @BeforeEach
  void setUp() {
    ChaosRelayProperties properties = new ChaosRelayProperties();
    properties.setAllowedTargetApplications(List.of("chaos-poc-demo"));
    validator = new ChaosCommandValidator(properties);
  }

  @Test
  void shouldRejectNonTestEnvironment() {
    ChaosCommandRequest request =
        new ChaosCommandRequest(
            null,
            "production",
            "chaos-poc-demo",
            ChaosCommandAction.DISABLE,
            null,
            null,
            "operator",
            null);

    assertThat(validator.validate(request))
        .anyMatch(e -> e.field().equals("environment"));
  }

  @Test
  void shouldRequireAssaultForConfigureAndEnable() {
    ChaosCommandRequest request =
        new ChaosCommandRequest(
            null,
            "test",
            "chaos-poc-demo",
            ChaosCommandAction.CONFIGURE_AND_ENABLE,
            null,
            Instant.now().plusSeconds(60),
            "operator",
            null);

    assertThat(validator.validate(request))
        .anyMatch(e -> e.field().equals("assault"));
  }
}
