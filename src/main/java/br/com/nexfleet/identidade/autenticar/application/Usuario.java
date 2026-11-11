package br.com.nexfleet.identidade.autenticar.application;

import br.com.nexfleet.identidade.usuario.domain.Perfil;
import java.util.UUID;

public record Usuario(UUID id, String email, String senhaHash, boolean ativo, Perfil perfil) {
  public Usuario(UUID id, String email, String senhaHash, boolean ativo) {
    this(id, email, senhaHash, ativo, Perfil.GESTOR_FROTA);
  }

  @Override
  public String toString() {
    return "Usuario[id=" + id + "]";
  }
}
