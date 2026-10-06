package com.samba.chaos.config;

import com.samba.chaos.web.ChaosCommandEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Bearer-JWT protection for the chaos command endpoint only. The chain is scoped with a security
 * matcher and ordered first so it never changes how the host application secures its own routes.
 * The JWT decoder comes from the host's {@code spring.security.oauth2.resourceserver.jwt.*}
 * properties.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class ChaosSecurityConfiguration {

  public static final String COMMAND_SCOPE = "chaos.command";
  public static final String COMMAND_AUTHORITY = "SCOPE_" + COMMAND_SCOPE;

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  SecurityFilterChain chaosCommandSecurityFilterChain(HttpSecurity http) {
    http.securityMatcher(ChaosCommandEndpoint.PATH, ChaosCommandEndpoint.PATH + "/**")
        .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
        .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(AbstractHttpConfigurer::disable);
    return http.build();
  }
}
