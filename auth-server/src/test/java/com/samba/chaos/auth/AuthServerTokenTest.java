package com.samba.chaos.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

@SpringBootTest(properties = "CHAOS_RELAY_CLIENT_SECRET=test-secret")
@AutoConfigureMockMvc
class AuthServerTokenTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void relayClientCredentialsGrantIssuesBearerTokenWithChaosScope() throws Exception {
    mockMvc
        .perform(tokenRequest("chaos-command-relay", "test-secret", "chaos.command"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.access_token").isNotEmpty())
        .andExpect(jsonPath("$.token_type").value("Bearer"))
        .andExpect(jsonPath("$.scope").value("chaos.command"));
  }

  @Test
  void wrongClientSecretIsUnauthorized() throws Exception {
    mockMvc
        .perform(tokenRequest("chaos-command-relay", "wrong", "chaos.command"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void unregisteredScopeIsRejected() throws Exception {
    mockMvc
        .perform(tokenRequest("chaos-command-relay", "test-secret", "chaos.admin"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("invalid_scope"));
  }

  @Test
  void jwkSetIsPublishedForResourceServers() throws Exception {
    mockMvc
        .perform(get("/oauth2/jwks"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keys[0].kty").value("RSA"));
  }

  @Test
  void healthIsPublicButOtherPathsAreDenied() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    mockMvc.perform(get("/anything")).andExpect(status().is4xxClientError());
  }

  private static RequestBuilder tokenRequest(String clientId, String secret, String scope) {
    String credentials = clientId + ":" + secret;
    return post("/oauth2/token")
        .header(
            HttpHeaders.AUTHORIZATION,
            "Basic "
                + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)))
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .param("grant_type", "client_credentials")
        .param("scope", scope);
  }
}
