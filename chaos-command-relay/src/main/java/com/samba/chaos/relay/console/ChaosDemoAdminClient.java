package com.samba.chaos.relay.console;

import com.samba.chaos.relay.ChaosRelayProperties;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class ChaosDemoAdminClient {

  private final ChaosRelayProperties properties;
  private final RestTemplate restTemplate;

  public ChaosDemoAdminClient(ChaosRelayProperties properties, RestTemplate restTemplate) {
    this.properties = properties;
    this.restTemplate = restTemplate;
  }

  public boolean resetDemoData(String applicationName) {
    String baseUrl = properties.getAdminBaseUrls().get(applicationName);
    if (baseUrl == null || baseUrl.isBlank()) {
      return false;
    }

    String url = baseUrl.replaceAll("/$", "") + "/api/v1/admin/reset";
    try {
      restTemplate.postForEntity(url, null, Map.class);
      return true;
    } catch (RestClientException ex) {
      throw new IllegalStateException(
          "Demo data reset failed for " + applicationName + ": " + ex.getMessage(), ex);
    }
  }
}
