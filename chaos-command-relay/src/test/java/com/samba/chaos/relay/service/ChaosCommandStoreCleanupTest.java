package com.samba.chaos.relay.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.samba.chaos.relay.config.ChaosRelayProperties;
import com.samba.chaos.relay.store.ChaosCommandStore;
import org.junit.jupiter.api.Test;

class ChaosCommandStoreCleanupTest {

  @Test
  void logsWhenExpiredRecordsAreRemoved() {
    ChaosCommandStore store = mock(ChaosCommandStore.class);
    when(store.deletePublishedBefore(any())).thenReturn(2);
    ChaosRelayProperties properties = new ChaosRelayProperties();
    properties.setCommandTtlHours(1);

    new ChaosCommandStoreCleanup(store, properties).removeExpiredHistory();

    verify(store).deletePublishedBefore(any());
  }
}
