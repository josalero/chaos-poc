package com.samba.chaos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class ChaosEnvironmentPostProcessorTest {

  private final ChaosEnvironmentPostProcessor processor = new ChaosEnvironmentPostProcessor();
  private final SpringApplication application = new SpringApplication();

  @Test
  void doesNothingWhenTheFlagIsUnset() {
    MockEnvironment environment = new MockEnvironment();

    processor.postProcessEnvironment(environment, application);

    assertThat(environment.getActiveProfiles()).isEmpty();
    assertThat(environment.getProperty("chaos.monkey.enabled")).isNull();
  }

  @Test
  void refusesProductionProfileBeforeAddingChaosMonkey() {
    MockEnvironment environment =
        new MockEnvironment().withProperty(ChaosEnvironmentPostProcessor.ENABLED, "true");
    environment.setActiveProfiles("production");

    assertThatThrownBy(() -> processor.postProcessEnvironment(environment, application))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("refused");
    assertThat(environment.getActiveProfiles()).containsExactly("production");
  }

  @Test
  void refusesProductionEnvironmentProperty() {
    MockEnvironment environment =
        new MockEnvironment()
            .withProperty(ChaosEnvironmentPostProcessor.ENABLED, "true")
            .withProperty(ChaosEnvironmentPostProcessor.ENVIRONMENT, "production");

    assertThatThrownBy(() -> processor.postProcessEnvironment(environment, application))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void addsTheChaosMonkeyProfileAndIdleDefaultsWhenEnabled() {
    MockEnvironment environment =
        new MockEnvironment().withProperty(ChaosEnvironmentPostProcessor.ENABLED, "true");

    processor.postProcessEnvironment(environment, application);

    assertThat(environment.getActiveProfiles()).contains("chaos-monkey");
    assertThat(environment.getProperty("chaos.monkey.enabled")).isEqualTo("false");
    assertThat(environment.getProperty("chaos.monkey.watcher.service")).isEqualTo("true");
    assertThat(environment.getProperty("chaos.monkey.watcher.rest-controller")).isEqualTo("false");
    assertThat(environment.getProperty("chaos.monkey.assaults.level")).isEqualTo("1");
    assertThat(environment.getProperty("management.endpoint.chaosmonkey.access"))
        .isEqualTo("unrestricted");
    assertThat(environment.getProperty(ChaosEnvironmentPostProcessor.EXPOSURE))
        .isEqualTo("health,chaosmonkey");
  }

  @Test
  void keepsHostValueAndDoesNotDuplicateTheActuatorEndpoint() {
    MockEnvironment environment =
        new MockEnvironment()
            .withProperty(ChaosEnvironmentPostProcessor.ENABLED, "TRUE")
            .withProperty("chaos.monkey.enabled", "true")
            .withProperty(
                ChaosEnvironmentPostProcessor.EXPOSURE, "health, prometheus, chaosmonkey");

    processor.postProcessEnvironment(environment, application);

    assertThat(environment.getProperty("chaos.monkey.enabled")).isEqualTo("true");
    assertThat(environment.getProperty(ChaosEnvironmentPostProcessor.EXPOSURE))
        .isEqualTo("health, prometheus, chaosmonkey");
  }

  @Test
  void runsLastSoApplicationPropertiesAreAlreadyVisible() {
    assertThat(processor.getOrder()).isEqualTo(Integer.MAX_VALUE);
  }
}
