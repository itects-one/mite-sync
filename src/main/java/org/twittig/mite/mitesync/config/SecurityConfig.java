package org.twittig.mite.mitesync.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Locks down every endpoint behind HTTP basic authentication.
 *
 * <p>The write paths create real entries in an external time-tracking system and the proposal
 * store holds persistent state, so the service must not be reachable unauthenticated once it runs
 * anywhere but localhost.
 *
 * <p>The single user comes from Spring Boot's own {@code spring.security.user.*} properties, which
 * are overridable by environment variable like every other secret in this project
 * ({@code SPRING_SECURITY_USER_NAME} / {@code SPRING_SECURITY_USER_PASSWORD}). They are
 * deliberately absent from the committed {@code application.yml}: an empty placeholder password
 * would be a weak credential, whereas leaving them unset makes Spring Boot generate a random
 * password and log it at startup — which fails safe rather than open.
 */
@Configuration
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        // Stateless REST API consumed by HTTP clients, not a browser form application: there is
        // no session to protect and no CSRF token for a client to carry.
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
        .httpBasic(Customizer.withDefaults())
        .build();
  }
}
