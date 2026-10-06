package com.samba.chaos.downstream.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The downstream API is an intentionally unauthenticated sample workload. chaos-lib registers its
 * own higher-precedence JWT chain for {@code /internal/chaos/**}; this chain covers everything
 * else.
 */
@Configuration(proxyBeanMethods = false)
class DownstreamSecurityConfig {

  @Bean
  SecurityFilterChain downstreamSecurityFilterChain(HttpSecurity http) {
    http.authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
        .csrf(AbstractHttpConfigurer::disable);
    return http.build();
  }
}
