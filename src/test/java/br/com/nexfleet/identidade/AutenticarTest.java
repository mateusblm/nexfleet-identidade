package br.com.nexfleet.identidade;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import br.com.nexfleet.identidade.autenticar.application.*;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AutenticarTest {
  final BuscarUsuario usuarios = mock(BuscarUsuario.class);
  final VerificarSenha senhas = mock(VerificarSenha.class);
  final EmitirToken tokens = mock(EmitirToken.class);
  final Autenticar caso = new Autenticar(usuarios, senhas, tokens);
  final UUID id = UUID.randomUUID();

  @Test
  void normalizaEmailEEmiteTokenApenasDepoisDeValidarSenha() {
    when(usuarios.porEmail("aluno@example.com"))
        .thenReturn(Optional.of(new Usuario(id, "aluno@example.com", "hash", true)));
    when(senhas.confere("senha", "hash")).thenReturn(true);
    var token = new EmitirToken.Token("jwt", "Bearer", 900);
    when(tokens.emitir(id, br.com.nexfleet.identidade.usuario.domain.Perfil.GESTOR_FROTA))
        .thenReturn(token);
    assertEquals(token, caso.executar(" ALUNO@example.com ", "senha"));
    verify(tokens).emitir(id, br.com.nexfleet.identidade.usuario.domain.Perfil.GESTOR_FROTA);
  }

  @Test
  void usuarioAusenteTambemVerificaSenhaESemEmitirToken() {
    when(usuarios.porEmail("x@example.com")).thenReturn(Optional.empty());
    assertThrows(
        Autenticar.CredenciaisInvalidas.class, () -> caso.executar("x@example.com", "senha"));
    verify(senhas).confere("senha", null);
    verifyNoInteractions(tokens);
  }

  @Test
  void senhaErradaOuUsuarioInativoNaoRecebeToken() {
    for (boolean ativo : new boolean[] {true, false}) {
      when(usuarios.porEmail("x@example.com"))
          .thenReturn(Optional.of(new Usuario(id, "x@example.com", "hash", ativo)));
      when(senhas.confere("senha", "hash")).thenReturn(!ativo);
      assertThrows(
          Autenticar.CredenciaisInvalidas.class, () -> caso.executar("x@example.com", "senha"));
    }
    verifyNoInteractions(tokens);
  }

  @Test
  void rejeitaEntradasVaziasESenhaAcimaDoLimiteEmBytes() {
    assertThrows(Autenticar.CredenciaisInvalidas.class, () -> caso.executar(null, "senha"));
    assertThrows(Autenticar.CredenciaisInvalidas.class, () -> caso.executar("x@example.com", ""));
    assertThrows(
        Autenticar.CredenciaisInvalidas.class,
        () -> caso.executar("x@example.com", "á".repeat(37)));
    verifyNoInteractions(usuarios, senhas, tokens);
  }

  @Test
  void toStringNaoRevelaCredenciais() {
    assertFalse(new Usuario(id, "email", "segredo", true).toString().contains("segredo"));
    assertFalse(new EmitirToken.Token("segredo", "Bearer", 900).toString().contains("segredo"));
  }
}
