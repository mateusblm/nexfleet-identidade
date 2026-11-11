package br.com.nexfleet.identidade.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    var converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(
        jwt -> {
          String perfil = jwt.getClaimAsString("perfil");
          if ("ADMINISTRADOR".equals(perfil) || "GESTOR_FROTA".equals(perfil))
            return List.of(new SimpleGrantedAuthority("ROLE_" + perfil));
          return List.of();
        });
    return http
        // API usa Bearer explícito, sem autenticação por cookies ou sessão.
        .csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .oauth2ResourceServer(
            resource -> resource.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.POST, "/api/v1/identidade/login")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/identidade/me")
                    .authenticated()
                    .requestMatchers(
                        "/api/v1/identidade/usuarios",
                        "/api/v1/identidade/usuarios/*",
                        "/api/v1/identidade/usuarios/*/definitivo")
                    .hasRole("ADMINISTRADOR")
                    .requestMatchers(
                        HttpMethod.GET,
                        "/.well-known/jwks.json",
                        "/actuator/health",
                        "/actuator/health/**",
                        "/actuator/prometheus",
                        "/api/v1/identidade/info")
                    .permitAll()
                    .anyRequest()
                    .denyAll())
        .build();
  }
}
