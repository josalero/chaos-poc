package com.samba.chaos.downstream.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class InventoryApiTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void stockReserveAndAccessFollowTheSampleRules() throws Exception {
    mockMvc
        .perform(get("/v1/inventory/ABC"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available").value(42));
    mockMvc.perform(get("/v1/inventory/not-found")).andExpect(status().isNotFound());
    mockMvc.perform(get("/v1/inventory/forbidden")).andExpect(status().isForbidden());

    String body = "{\"sku\":\"abc\",\"quantity\":1}";
    mockMvc
        .perform(
            post("/v1/inventory/reserve").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("RESERVED"));
    mockMvc
        .perform(
            post("/v1/inventory/reserve").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isConflict());
    mockMvc
        .perform(
            post("/v1/inventory/reserve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"conflict\",\"quantity\":2}"))
        .andExpect(status().isConflict());

    mockMvc
        .perform(get("/v1/auth/catalog"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.access").value("granted"));
    mockMvc.perform(get("/v1/auth/restricted")).andExpect(status().isForbidden());

    mockMvc.perform(post("/api/v1/admin/reset")).andExpect(status().isOk());
    mockMvc
        .perform(
            post("/v1/inventory/reserve").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated());
  }
}
