package br.com.nexfleet.identidade.config;

import br.com.nexfleet.identidade.usuario.domain.Perfil;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Provisionamento explícito para estudo local. Nunca habilitar na Azure. */
@Configuration
@Profile("local-seed")
public class LocalSeedConfig {
  @Bean
  ApplicationRunner criarUsuarioLocal(
      JdbcTemplate jdbc,
      @Value("${LOCAL_USER_EMAIL}") String email,
      @Value("${LOCAL_USER_PASSWORD}") String senha,
      @Value("${LOCAL_USER_PROFILE:GESTOR_FROTA}") Perfil perfil) {
    return args -> {
      String normalizado = email.strip().toLowerCase(Locale.ROOT);
      if (!normalizado.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
          || normalizado.length() > 254
          || senha.length() < 12
          || senha.getBytes(StandardCharsets.UTF_8).length > 72) {
        throw new IllegalArgumentException(
            "Usuário local exige e-mail válido e senha de 12 caracteres a 72 bytes.");
      }
      // Uma segunda execução nunca muda a senha nem reativa uma conta existente.
      jdbc.update(
          "INSERT INTO identidade.usuario(id, email, senha_hash, perfil) VALUES (?, ?, ?, ?) ON"
              + " CONFLICT(email) DO NOTHING",
          UUID.randomUUID(),
          normalizado,
          new BCryptPasswordEncoder(12).encode(senha),
          perfil.name());
    };
  }
}
