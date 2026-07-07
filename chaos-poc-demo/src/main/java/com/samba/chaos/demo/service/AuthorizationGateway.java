package com.samba.chaos.demo.service;

import com.samba.chaos.demo.client.AuthorizationClient;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationGateway {

  private final AuthorizationClient authorizationClient;

  public AuthorizationGateway(AuthorizationClient authorizationClient) {
    this.authorizationClient = authorizationClient;
  }

  public Map<String, String> checkAccess(String resource) {
    return authorizationClient.checkAccess(resource);
  }
}
