package com.samba.chaos.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Only machine-to-machine client_credentials is offered, so there is no login page: everything
 * outside the OAuth2 endpoints and the health probe is denied.
 */
@Configuration(proxyBeanMethods = false)
class AuthServerSecurityConfig {

  @Bean
  @Order(1)
  SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) {
    http.oauth2AuthorizationServer(
            authorizationServer -> http.securityMatcher(authorizationServer.getEndpointsMatcher()))
        .authorizeHttpRequests(requests -> requests.anyRequest().authenticated());
    return http.build();
  }

  @Bean
  @Order(2)
  SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) {
    http.authorizeHttpRequests(
        requests ->
            requests
                .requestMatchers("/actuator/health", "/actuator/health/**")
                .permitAll()
                .anyRequest()
                .denyAll());
    return http.build();
  }
}
