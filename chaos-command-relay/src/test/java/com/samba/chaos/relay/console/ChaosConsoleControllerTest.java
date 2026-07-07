package com.samba.chaos.relay.console;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.samba.chaos.relay.model.ServiceConfigState;
import com.samba.chaos.relay.model.ChaosServiceStatusSummary;
import com.samba.chaos.relay.ChaosRelayProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ChaosConsoleController.class)
class ChaosConsoleControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private ChaosConsoleService consoleService;

  @MockBean private ChaosRelayProperties relayProperties;

  @Test
  void dashboard_returnsServiceList() throws Exception {
    when(relayProperties.getVerifyUiUrl()).thenReturn("http://localhost:18000");
    when(consoleService.listServices())
        .thenReturn(
            List.of(
                new ChaosServiceStatusSummary(
                    "chaos-poc-demo", true, ServiceConfigState.DEFAULT, false, null, null, 1, 1)));

    mockMvc
        .perform(get("/chaos"))
        .andExpect(status().isOk())
        .andExpect(view().name("chaos/dashboard"))
        .andExpect(model().attributeExists("services"));
  }
}
