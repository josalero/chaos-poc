package com.samba.chaos.relay.console;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.samba.chaos.relay.config.ChaosRelayProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChaosMonkeyStatusCacheTest {

  @Test
  void refreshStoresOnOffAndUnknown() {
    ChaosRelayProperties properties = new ChaosRelayProperties();
    properties.setAllowedTargetApplications(List.of("orders", "billing", "missing"));
    ChaosMonkeyActuatorProbe probe = mock(ChaosMonkeyActuatorProbe.class);
    when(probe.readEnabled("orders"))
        .thenReturn(new ChaosMonkeyActuatorProbe.EnabledRead(true, 2, 2));
    when(probe.readEnabled("billing"))
        .thenReturn(new ChaosMonkeyActuatorProbe.EnabledRead(false, 1, 1));
    when(probe.readEnabled("missing"))
        .thenReturn(new ChaosMonkeyActuatorProbe.EnabledRead(null, 0, 2));
    ChaosMonkeyStatusCache cache = new ChaosMonkeyStatusCache(properties, probe);

    cache.refreshAll();

    assertThat(cache.get("orders").orElseThrow().enabled()).isTrue();
    assertThat(cache.get("billing").orElseThrow().enabled()).isFalse();
    assertThat(cache.get("missing").orElseThrow().enabled()).isNull();
    assertThat(cache.get("missing").orElseThrow().checkedAt()).isNotNull();
    assertThat(cache.get("unknown")).isEmpty();

    when(probe.readEnabled("orders")).thenThrow(new IllegalStateException("down"));
    cache.refresh("orders");
    assertThat(cache.get("orders").orElseThrow().enabled()).isTrue();
  }
}
