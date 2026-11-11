package br.com.nexfleet.identidade.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import br.com.nexfleet.identidade.usuario.domain.Perfil;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class LocalSeedConfigTest {
  @Test
  void seedNormalizaEmailUsaHashENaoSobrescreveContaExistente() throws Exception {
    var jdbc = mock(JdbcTemplate.class);
    when(jdbc.update(anyString(), any(UUID.class), anyString(), anyString(), anyString()))
        .thenAnswer(
            invocation -> {
              assertTrue(
                  invocation.<String>getArgument(0).contains("ON CONFLICT(email) DO NOTHING"));
              assertEquals("aluno@example.com", invocation.<String>getArgument(2));
              assertEquals("ADMINISTRADOR", invocation.<String>getArgument(4));
              assertTrue(
                  new BCryptPasswordEncoder()
                      .matches("senha-local-estudo", invocation.getArgument(3)));
              return 0;
            });
    new LocalSeedConfig()
        .criarUsuarioLocal(jdbc, " ALUNO@example.com ", "senha-local-estudo", Perfil.ADMINISTRADOR)
        .run(null);
    verify(jdbc)
        .update(
            anyString(),
            any(UUID.class),
            eq("aluno@example.com"),
            anyString(),
            eq("ADMINISTRADOR"));
  }

  @Test
  void rejeitaSeedInvalidoAntesDePersistir() {
    var jdbc = mock(JdbcTemplate.class);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LocalSeedConfig()
                .criarUsuarioLocal(jdbc, "invalido", "curta", Perfil.GESTOR_FROTA)
                .run(null));
    verifyNoInteractions(jdbc);
  }
}
