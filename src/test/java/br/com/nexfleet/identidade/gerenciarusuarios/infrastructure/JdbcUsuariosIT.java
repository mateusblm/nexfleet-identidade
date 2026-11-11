package br.com.nexfleet.identidade.gerenciarusuarios.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import br.com.nexfleet.identidade.SqlServerIT;
import br.com.nexfleet.identidade.autenticar.application.BuscarUsuario;
import br.com.nexfleet.identidade.gerenciarusuarios.application.FalhaUsuario;
import br.com.nexfleet.identidade.gerenciarusuarios.application.Usuarios;
import br.com.nexfleet.identidade.gerenciarusuarios.application.Usuarios.Dados;
import br.com.nexfleet.identidade.usuario.domain.Perfil;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Porta de usuários (gestão e autenticação) contra o schema identidade no SQL Server. */
class JdbcUsuariosIT extends SqlServerIT {
  static final String HASH = "$2a$04$abcdefghijklmnopqrstuuW1vXb2YcZ3./0123456789ABCDEFGHI";

  @Autowired Usuarios usuarios;
  @Autowired BuscarUsuario buscarUsuario;
  @Autowired JdbcTemplate jdbc;

  @AfterEach
  void limpar() {
    jdbc.update("DELETE FROM identidade.usuario");
  }

  @Test
  void criaEBuscaUsuarioAtivo() {
    var id = UUID.randomUUID();
    var criado = usuarios.criar(id, "ana@example.com", HASH, Perfil.ADMINISTRADOR);
    var esperado = new Dados(id, "ana@example.com", Perfil.ADMINISTRADOR, true);
    assertEquals(esperado, criado);
    assertEquals(Optional.of(esperado), usuarios.buscar(id));
    assertEquals(Optional.empty(), usuarios.buscar(UUID.randomUUID()));
  }

  @Test
  void buscaParaAutenticacaoPorEmailComHashEPerfil() {
    var id = UUID.randomUUID();
    usuarios.criar(id, "bia@example.com", HASH, Perfil.GESTOR_FROTA);
    var usuario = buscarUsuario.porEmail("bia@example.com").orElseThrow();
    assertEquals(id, usuario.id());
    assertEquals(HASH, usuario.senhaHash());
    assertTrue(usuario.ativo());
    assertEquals(Perfil.GESTOR_FROTA, usuario.perfil());
    assertTrue(buscarUsuario.porEmail("outra@example.com").isEmpty());
  }

  @Test
  void listaPaginadaPorEmail() {
    var c = usuarios.criar(UUID.randomUUID(), "c@example.com", HASH, Perfil.GESTOR_FROTA);
    var a = usuarios.criar(UUID.randomUUID(), "a@example.com", HASH, Perfil.GESTOR_FROTA);
    var b = usuarios.criar(UUID.randomUUID(), "b@example.com", HASH, Perfil.GESTOR_FROTA);
    assertEquals(List.of(a, b, c), usuarios.listar(0, 10));
    assertEquals(List.of(b), usuarios.listar(1, 1));
    assertEquals(List.of(), usuarios.listar(3, 10));
  }

  @Test
  void atualizaEmailPerfilESituacao() {
    var id = UUID.randomUUID();
    usuarios.criar(id, "caio@example.com", HASH, Perfil.GESTOR_FROTA);
    var esperado = new Dados(id, "caio.novo@example.com", Perfil.ADMINISTRADOR, false);
    assertEquals(
        Optional.of(esperado),
        usuarios.atualizar(id, "caio.novo@example.com", Perfil.ADMINISTRADOR, false));
    assertEquals(Optional.of(esperado), usuarios.buscar(id));
    assertEquals(
        Optional.empty(),
        usuarios.atualizar(UUID.randomUUID(), "x@example.com", Perfil.GESTOR_FROTA, true));
  }

  @Test
  void desativaERevogaAdministrador() {
    var id = UUID.randomUUID();
    usuarios.criar(id, "dani@example.com", HASH, Perfil.ADMINISTRADOR);
    assertTrue(usuarios.administradorAtivo(id));
    assertTrue(usuarios.desativar(id));
    assertFalse(usuarios.buscar(id).orElseThrow().ativo());
    assertFalse(usuarios.administradorAtivo(id));
    assertFalse(usuarios.desativar(UUID.randomUUID()));
  }

  @Test
  void gestorNaoEhAdministradorAtivo() {
    var id = UUID.randomUUID();
    usuarios.criar(id, "edu@example.com", HASH, Perfil.GESTOR_FROTA);
    assertFalse(usuarios.administradorAtivo(id));
    assertFalse(usuarios.administradorAtivo(UUID.randomUUID()));
  }

  @Test
  void excluiSomenteContaDesativada() {
    var id = UUID.randomUUID();
    usuarios.criar(id, "fabi@example.com", HASH, Perfil.GESTOR_FROTA);
    assertEquals(Usuarios.Exclusao.NAO_INATIVO, usuarios.excluir(id));
    usuarios.desativar(id);
    assertEquals(Usuarios.Exclusao.EXCLUIDO, usuarios.excluir(id));
    assertEquals(Usuarios.Exclusao.NAO_ENCONTRADO, usuarios.excluir(id));
  }

  @Test
  void emailDuplicadoNaCriacaoENaAtualizacaoViraConflito() {
    var id = UUID.randomUUID();
    usuarios.criar(id, "gil@example.com", HASH, Perfil.GESTOR_FROTA);
    var outro = UUID.randomUUID();
    usuarios.criar(outro, "hugo@example.com", HASH, Perfil.GESTOR_FROTA);
    var criar =
        assertThrows(
            FalhaUsuario.class,
            () -> usuarios.criar(UUID.randomUUID(), "gil@example.com", HASH, Perfil.GESTOR_FROTA));
    assertEquals(FalhaUsuario.Tipo.CONFLITO, criar.tipo());
    var atualizar =
        assertThrows(
            FalhaUsuario.class,
            () -> usuarios.atualizar(outro, "gil@example.com", Perfil.GESTOR_FROTA, true));
    assertEquals(FalhaUsuario.Tipo.CONFLITO, atualizar.tipo());
  }

  @Test
  void bancoRecusaEmailNaoNormalizado() {
    for (var email : List.of("Ivo@example.com", " ivo@example.com", "ivo@example.com "))
      assertThrows(
          DataIntegrityViolationException.class,
          () -> usuarios.criar(UUID.randomUUID(), email, HASH, Perfil.GESTOR_FROTA),
          email);
    assertEquals(List.of(), usuarios.listar(0, 10));
  }

  @Test
  void bancoRecusaHashQueNaoEhBcrypt() {
    var invalidos =
        List.of(
            "senha-em-texto-puro",
            "$2x$04$abcdefghijklmnopqrstuuW1vXb2YcZ3./0123456789ABCDEFGHI",
            "$2a$4a$abcdefghijklmnopqrstuuW1vXb2YcZ3./0123456789ABCDEFGHI",
            "$2a$04$abcdefghijklmnopqrstuuW1vXb2YcZ3./0123456789ABCDEFGH!",
            "$2a$04$abcdefghijklmnopqrstuuW1vXb2YcZ3./0123456789ABCDEFGH",
            "$2a$04$abcdefghijklmnopqrstuuW1vXb2YcZ3./0123456789ABCDEFGH ",
            "$2a$04$abcdefghijklmnopqrstuuW1vXb2YcZ3./0123456789ABCDEFGHÍ");
    for (var hash : invalidos)
      assertThrows(
          DataIntegrityViolationException.class,
          () -> usuarios.criar(UUID.randomUUID(), "joao@example.com", hash, Perfil.GESTOR_FROTA),
          hash);
    assertEquals(List.of(), usuarios.listar(0, 10));
  }

  @Test
  void bancoRecusaPerfilForaDosValoresConhecidos() {
    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            jdbc.update(
                "INSERT INTO identidade.usuario(id,email,senha_hash,perfil) VALUES (?,?,?,?)",
                UUID.randomUUID(),
                "kai@example.com",
                HASH,
                "ROOT"));
  }
}
