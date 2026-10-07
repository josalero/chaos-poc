package com.samba.chaos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Bearer-JWT protection for the chaos command endpoint only. The chain is scoped with a security
 * matcher and ordered first so it never changes how the host application secures its own routes.
 * The JWT decoder comes from the host's {@code spring.security.oauth2.resourceserver.jwt.*}
 * properties. Scope is checked on this chain so the library does not enable method security for the
 * host.
 */
@Configuration(proxyBeanMethods = false)
public class ChaosSecurityConfiguration {

  public static final String COMMAND_SCOPE = "chaos.command";
  public static final String COMMAND_AUTHORITY = "SCOPE_" + COMMAND_SCOPE;

  /**
   * Secures {@code /internal/chaos/**} with {@code SCOPE_chaos.command}.
   *
   * @param http security builder for this chain only
   * @return the chaos command filter chain
   * @throws Exception when the HTTP security builder fails
   */
  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  SecurityFilterChain chaosCommandSecurityFilterChain(HttpSecurity http) throws Exception {
    http.securityMatcher("/internal/chaos/**")
        .authorizeHttpRequests(requests -> requests.anyRequest().hasAuthority(COMMAND_AUTHORITY))
        .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(AbstractHttpConfigurer::disable);
    return http.build();
  }
}
