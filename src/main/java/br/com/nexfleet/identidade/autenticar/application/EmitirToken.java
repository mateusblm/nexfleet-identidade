package br.com.nexfleet.identidade.autenticar.application;

import br.com.nexfleet.identidade.usuario.domain.Perfil;
import java.util.UUID;

public interface EmitirToken {
  Token emitir(UUID usuarioId, Perfil perfil);

  default Token emitir(UUID usuarioId) {
    return emitir(usuarioId, Perfil.GESTOR_FROTA);
  }

  record Token(String accessToken, String tokenType, long expiresIn) {
    @Override
    public String toString() {
      return "Token[redigido]";
    }
  }
}
