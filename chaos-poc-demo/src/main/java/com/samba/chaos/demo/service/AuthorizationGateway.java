package com.samba.chaos.demo.service;

import com.samba.chaos.demo.client.AuthorizationClient;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Member. */
@Service
public class AuthorizationGateway {

  private final AuthorizationClient authorizationClient;

  /** Authorization Gateway. */
  public AuthorizationGateway(AuthorizationClient authorizationClient) {
    this.authorizationClient = authorizationClient;
  }

  /** Check Access. */
  public Map<String, String> checkAccess(String resource) {
    return authorizationClient.checkAccess(resource);
  }
}
