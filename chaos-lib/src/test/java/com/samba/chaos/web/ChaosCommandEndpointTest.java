package com.samba.chaos.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.samba.chaos.command.ChaosCommandAction;
import com.samba.chaos.command.ChaosCommandMessage;
import com.samba.chaos.command.ChaosCommandResult;
import com.samba.chaos.command.InstanceOutcome;
import com.samba.chaos.config.ChaosSecurityConfiguration;
import com.samba.chaos.service.ChaosCommandApplier;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(
    classes = ChaosCommandEndpointTest.HostApplication.class,
    properties = {
      "spring.application.name=orders",
      "samba.chaos.command.enabled=true",
      "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:9/oauth2/jwks"
    })
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ChaosCommandEndpointTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private ChaosCommandApplier applier;

  @Test
  void requestWithoutBearerTokenIsUnauthorizedAndNotApplied() throws Exception {
    mockMvc.perform(command(UUID.randomUUID())).andExpect(status().isUnauthorized());

    verify(applier, never()).apply(any());
  }

  @Test
  void jwtWithoutChaosScopeIsForbiddenAndNotApplied() throws Exception {
    mockMvc
        .perform(
            command(UUID.randomUUID())
                .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders.read"))))
        .andExpect(status().isForbidden());

    verify(applier, never()).apply(any());
  }

  @Test
  void jwtWithChaosScopeAppliesTheCommand() throws Exception {
    UUID commandId = UUID.randomUUID();
    when(applier.apply(any(ChaosCommandMessage.class)))
        .thenReturn(result(commandId, InstanceOutcome.SUCCESS, null));

    mockMvc
        .perform(command(commandId).with(chaosScope()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome").value("SUCCESS"))
        .andExpect(jsonPath("$.podName").value("pod-a"));
  }

  @Test
  void rejectedCommandReturnsConflict() throws Exception {
    UUID commandId = UUID.randomUUID();
    when(applier.apply(any(ChaosCommandMessage.class)))
        .thenReturn(result(commandId, InstanceOutcome.REJECTED, "application mismatch"));

    mockMvc
        .perform(command(commandId).with(chaosScope()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.outcome").value("REJECTED"));
  }

  @Test
  void hostRoutesOutsideTheChaosPathAreNotSecuredByTheLibrary() throws Exception {
    mockMvc
        .perform(get("/host/ping"))
        .andExpect(status().isOk())
        .andExpect(content().string("pong"));
  }

  private static RequestPostProcessor chaosScope() {
    return jwt()
        .authorities(new SimpleGrantedAuthority(ChaosSecurityConfiguration.COMMAND_AUTHORITY));
  }

  private static ChaosCommandResult result(UUID commandId, InstanceOutcome outcome, String reason) {
    return new ChaosCommandResult(
        commandId, "orders", "pod-a", outcome, reason, 200, Instant.parse("2026-10-05T12:00:00Z"));
  }

  private static MockHttpServletRequestBuilder command(UUID commandId) {
    return post(ChaosCommandEndpoint.PATH)
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            """
            {
              "commandId": "%s",
              "environment": "test",
              "targetApplication": "orders",
              "action": "%s",
              "issuedBy": "tester"
            }
            """
                .formatted(commandId, ChaosCommandAction.DISABLE));
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import(HostController.class)
  static class HostApplication {

    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }

  @RestController
  static class HostController {

    @GetMapping("/host/ping")
    String ping() {
      return "pong";
    }
  }
}
