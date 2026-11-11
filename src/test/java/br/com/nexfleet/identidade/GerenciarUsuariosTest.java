package br.com.nexfleet.identidade;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import br.com.nexfleet.identidade.gerenciarusuarios.application.*;
import br.com.nexfleet.identidade.usuario.domain.Perfil;
import java.util.*;
import org.junit.jupiter.api.*;

class GerenciarUsuariosTest {
  final UUID admin = UUID.randomUUID();
  final Usuarios store = mock(Usuarios.class);
  final ProtegerSenha senhas = mock(ProtegerSenha.class);
  final GerenciarUsuarios caso = new GerenciarUsuarios(store, senhas);

  @BeforeEach
  void setup() {
    when(store.administradorAtivo(admin)).thenReturn(true);
  }

  @Test
  void normalizaEmailEGravaSomenteHash() {
    when(senhas.hash("senha-nova-segura")).thenReturn("hash-bcrypt");
    caso.criar(admin, " NOVO@example.com ", "senha-nova-segura", Perfil.GESTOR_FROTA);
    verify(store)
        .criar(any(UUID.class), eq("novo@example.com"), eq("hash-bcrypt"), eq(Perfil.GESTOR_FROTA));
  }

  @Test
  void gestorInativoOuRebaixadoNaoConsultaNemEscreve() {
    when(store.administradorAtivo(admin)).thenReturn(false);
    assertEquals(
        FalhaUsuario.Tipo.PROIBIDO,
        assertThrows(FalhaUsuario.class, () -> caso.listar(admin, 0, 20)).tipo());
    assertThrows(
        FalhaUsuario.class,
        () -> caso.criar(admin, "x@example.com", "senha-nova-segura", Perfil.ADMINISTRADOR));
    assertThrows(
        FalhaUsuario.class,
        () -> caso.atualizar(admin, UUID.randomUUID(), "x@example.com", Perfil.GESTOR_FROTA, true));
    assertThrows(FalhaUsuario.class, () -> caso.desativar(admin, UUID.randomUUID()));
    verify(store, never()).listar(anyInt(), anyInt());
    verify(store, never()).criar(any(), anyString(), anyString(), any());
    verify(store, never()).atualizar(any(), anyString(), any(), anyBoolean());
    verify(store, never()).desativar(any());
    verifyNoInteractions(senhas);
  }

  @Test
  void entradasInvalidasNaoProduzemHashNemEscrita() {
    assertThrows(
        FalhaUsuario.class,
        () -> caso.criar(admin, "invalido", "senha-nova-segura", Perfil.GESTOR_FROTA));
    assertThrows(
        FalhaUsuario.class, () -> caso.criar(admin, "x@example.com", "curta", Perfil.GESTOR_FROTA));
    assertThrows(
        FalhaUsuario.class,
        () -> caso.criar(admin, "x@example.com", "á".repeat(37), Perfil.GESTOR_FROTA));
    assertThrows(
        FalhaUsuario.class, () -> caso.criar(admin, "x@example.com", "senha-nova-segura", null));
    verifyNoInteractions(senhas);
    verify(store, never()).criar(any(), anyString(), anyString(), any());
  }

  @Test
  void paginacaoLimitadaEOrdenacaoDelegada() {
    caso.listar(admin, 2, 20);
    verify(store).listar(40, 20);
    assertThrows(FalhaUsuario.class, () -> caso.listar(admin, -1, 20));
    assertThrows(FalhaUsuario.class, () -> caso.listar(admin, 0, 101));
    assertThrows(FalhaUsuario.class, () -> caso.listar(admin, Integer.MAX_VALUE, 100));
  }

  @Test
  void atualizacaoAusenteEDesativacaoAusenteRetornamNaoEncontrado() {
    UUID id = UUID.randomUUID();
    when(store.atualizar(id, "novo@example.com", Perfil.GESTOR_FROTA, false))
        .thenReturn(Optional.empty());
    assertEquals(
        FalhaUsuario.Tipo.NAO_ENCONTRADO,
        assertThrows(
                FalhaUsuario.class,
                () -> caso.atualizar(admin, id, "novo@example.com", Perfil.GESTOR_FROTA, false))
            .tipo());
    assertEquals(
        FalhaUsuario.Tipo.NAO_ENCONTRADO,
        assertThrows(FalhaUsuario.class, () -> caso.desativar(admin, id)).tipo());
  }

  @Test
  void excluiSomenteContaDesativadaENuncaAPropria() {
    var id = UUID.randomUUID();
    when(store.administradorAtivo(admin)).thenReturn(true);
    when(store.excluir(id))
        .thenReturn(Usuarios.Exclusao.EXCLUIDO, Usuarios.Exclusao.NAO_INATIVO, Usuarios.Exclusao.NAO_ENCONTRADO);
    assertDoesNotThrow(() -> caso.excluir(admin, id));
    assertEquals(
        FalhaUsuario.Tipo.INVALIDO,
        assertThrows(FalhaUsuario.class, () -> caso.excluir(admin, id)).tipo());
    assertEquals(
        FalhaUsuario.Tipo.NAO_ENCONTRADO,
        assertThrows(FalhaUsuario.class, () -> caso.excluir(admin, id)).tipo());
    assertEquals(
        FalhaUsuario.Tipo.INVALIDO,
        assertThrows(FalhaUsuario.class, () -> caso.excluir(admin, admin)).tipo());
    verify(store, never()).excluir(admin);
  }

  @Test
  void exclusaoExigeAdministradorAtivo() {
    when(store.administradorAtivo(admin)).thenReturn(false);
    assertEquals(
        FalhaUsuario.Tipo.PROIBIDO,
        assertThrows(FalhaUsuario.class, () -> caso.excluir(admin, UUID.randomUUID())).tipo());
    verify(store, never()).excluir(any());
  }
}
