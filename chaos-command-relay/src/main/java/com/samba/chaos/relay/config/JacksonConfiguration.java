package com.samba.chaos.relay.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/** Jackson settings for the relay. */
@Configuration
public class JacksonConfiguration {

  /**
   * Registers the application {@link JsonMapper}.
   *
   * <p>The return type is {@link JsonMapper}, not the {@code ObjectMapper} interface, so Spring
   * Boot's {@code JacksonAutoConfiguration} sees a {@code JsonMapper} bean and does not create a
   * second one.
   *
   * @return the mapper used by the relay
   */
  @Bean
  @Primary
  public JsonMapper objectMapper() {
    return JsonMapper.builder()
        .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
        .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();
  }
}
