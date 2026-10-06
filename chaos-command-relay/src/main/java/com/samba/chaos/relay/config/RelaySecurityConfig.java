package com.samba.chaos.relay.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security is on the relay's classpath only so it can act as an OAuth2 client toward
 * chaos-lib instances. This chain keeps the relay API open. The operator console is the separate
 * chaos-command-relay-ui app. Putting authentication in front of the API is a separate change.
 */
@Configuration(proxyBeanMethods = false)
public class RelaySecurityConfig {

  @Bean
  SecurityFilterChain relaySecurityFilterChain(HttpSecurity http) {
    http.authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
        .csrf(AbstractHttpConfigurer::disable);
    return http.build();
  }
}
