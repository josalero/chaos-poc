package com.samba.chaos.relay.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class JacksonConfigurationTest {

  @Test
  void writesInstantsAsIso8601AndIgnoresUnknownProperties() {
    JsonMapper mapper = new JacksonConfiguration().objectMapper();

    assertThat(mapper.writeValueAsString(Instant.parse("2026-10-05T12:00:00Z")))
        .isEqualTo("\"2026-10-05T12:00:00Z\"");
    assertThat(mapper.readValue("{\"name\":\"relay\",\"extra\":true}", Named.class).name())
        .isEqualTo("relay");
  }

  private record Named(String name) {}
}
