package br.com.nexfleet.identidade.autenticar.application;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class Autenticar {
  private final BuscarUsuario usuarios;
  private final VerificarSenha senhas;
  private final EmitirToken tokens;

  public Autenticar(BuscarUsuario usuarios, VerificarSenha senhas, EmitirToken tokens) {
    this.usuarios = usuarios;
    this.senhas = senhas;
    this.tokens = tokens;
  }

  public EmitirToken.Token executar(String email, String senha) {
    if (email == null
        || email.isBlank()
        || email.length() > 254
        || senha == null
        || senha.isEmpty()
        || senha.getBytes(StandardCharsets.UTF_8).length > 72) {
      throw new CredenciaisInvalidas();
    }
    var usuario = usuarios.porEmail(email.strip().toLowerCase(Locale.ROOT));
    // Mesmo usuário ausente percorre BCrypt para reduzir diferenças de tempo.
    boolean senhaValida = senhas.confere(senha, usuario.map(Usuario::senhaHash).orElse(null));
    if (usuario.isEmpty() || !senhaValida || !usuario.get().ativo())
      throw new CredenciaisInvalidas();
    return tokens.emitir(usuario.get().id(), usuario.get().perfil());
  }

  public static class CredenciaisInvalidas extends RuntimeException {
    public CredenciaisInvalidas() {
      super("Credenciais inválidas.");
    }
  }
}
