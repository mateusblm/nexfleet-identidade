package br.com.nexfleet.identidade.config;

import static org.junit.jupiter.api.Assertions.*;

import br.com.nexfleet.identidade.SqlServerIT;
import br.com.nexfleet.identidade.autenticar.application.BuscarUsuario;
import br.com.nexfleet.identidade.gerenciarusuarios.application.Usuarios;
import br.com.nexfleet.identidade.usuario.domain.Perfil;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Seed local repetido ou concorrente no SQL Server deixa uma única conta, sem trocar a senha. */
class LocalSeedConfigIT extends SqlServerIT {
  @Autowired JdbcTemplate jdbc;
  @Autowired Usuarios usuarios;
  @Autowired BuscarUsuario buscarUsuario;

  @AfterEach
  void limpar() {
    jdbc.update("DELETE FROM identidade.usuario");
  }

  @Test
  void seedDuasVezesMantemUmAdministradorComASenhaOriginal() throws Exception {
    seed(" Admin.Local@Example.com ", "senha-local-primeira");
    seed("admin.local@example.com", "senha-local-segunda");
    assertUmAdministradorCom("senha-local-primeira");
  }

  @Test
  void seedEmParaleloCriaUmUnicoAdministrador() throws Exception {
    var largada = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    try {
      Callable<Void> tarefa =
          () -> {
            largada.await();
            seed("admin.local@example.com", "senha-local-paralela");
            return null;
          };
      var a = executor.submit(tarefa);
      var b = executor.submit(tarefa);
      largada.countDown();
      a.get();
      b.get();
    } finally {
      executor.shutdownNow();
    }
    assertUmAdministradorCom("senha-local-paralela");
  }

  private void seed(String email, String senha) throws Exception {
    new LocalSeedConfig().criarUsuarioLocal(jdbc, email, senha, Perfil.ADMINISTRADOR).run(null);
  }

  private void assertUmAdministradorCom(String senha) {
    var todos = usuarios.listar(0, 10);
    assertEquals(1, todos.size(), todos.toString());
    assertEquals("admin.local@example.com", todos.get(0).email());
    assertEquals(Perfil.ADMINISTRADOR, todos.get(0).perfil());
    assertTrue(todos.get(0).ativo());
    var hash = buscarUsuario.porEmail("admin.local@example.com").orElseThrow().senhaHash();
    assertTrue(new BCryptPasswordEncoder().matches(senha, hash));
  }
}
