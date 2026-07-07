package com.samba.chaos.downstream.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.samba.chaos.downstream.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminResetController.class)
class AdminResetControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private InventoryService inventoryService;

  @Test
  void reset_shouldClearReservations() throws Exception {
    mockMvc
        .perform(post("/api/v1/admin/reset"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("OK"));
  }
}
