package com.samba.chaos.relay.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.samba.chaos.command.ChaosAssaultConfig;
import com.samba.chaos.command.ChaosCommandAction;
import com.samba.chaos.relay.config.ChaosRelayProperties;
import com.samba.chaos.relay.config.JacksonConfiguration;
import com.samba.chaos.relay.model.ChaosCommandRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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

    assertThat(validator.validate(request)).anyMatch(e -> e.field().equals("environment"));
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

    assertThat(validator.validate(request)).anyMatch(e -> e.field().equals("assault"));
  }

  @Test
  void shouldRejectUnknownApplicationMissingExpiryAndInvalidAssault() {
    ChaosAssaultConfig assault = new ChaosAssaultConfig(0, null, null, true, 50, 10, null, null);
    ChaosCommandRequest request =
        new ChaosCommandRequest(
            null,
            "test",
            "billing",
            ChaosCommandAction.ENABLE,
            assault,
            Instant.now().minusSeconds(5),
            "operator",
            "corr");

    assertThat(validator.validate(request))
        .extracting(error -> error.field())
        .contains("targetApplication", "expiresAt", "assault.level", "assault.latencyRange");
  }

  @Test
  void shouldRejectEnableWithoutExpiryAndLatencyWithoutRange() {
    ChaosAssaultConfig assault =
        new ChaosAssaultConfig(1, true, false, true, null, null, List.of(), null);
    ChaosCommandRequest request =
        new ChaosCommandRequest(
            null,
            "test",
            "chaos-poc-demo",
            ChaosCommandAction.CONFIGURE_AND_ENABLE,
            assault,
            null,
            "operator",
            null);

    assertThat(validator.validate(request))
        .extracting(error -> error.field())
        .contains("expiresAt", "assault.latencyRange");
  }

  @Test
  void shouldBuildMessageAndErrorResponse() throws Exception {
    ChaosAssaultConfig assault =
        new ChaosAssaultConfig(
            1,
            true,
            true,
            false,
            null,
            null,
            List.of("com.example.OrderService.place"),
            new JacksonConfiguration()
                .objectMapper()
                .readTree("{\"type\":\"java.lang.IllegalStateException\"}"));
    UUID commandId = UUID.randomUUID();
    ChaosCommandRequest request =
        new ChaosCommandRequest(
            commandId,
            "test",
            "chaos-poc-demo",
            ChaosCommandAction.CONFIGURE,
            assault,
            null,
            "operator",
            "corr");

    assertThat(validator.validate(request)).isEmpty();
    assertThat(validator.toMessage(request, commandId).commandId()).isEqualTo(commandId);
    assertThat(validator.toErrorResponse(List.of()).status()).isEqualTo("REJECTED");
  }
}
