package br.com.nexfleet.identidade.gerenciarusuarios.application;

import br.com.nexfleet.identidade.usuario.domain.Perfil;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface Usuarios {
  record Dados(UUID id, String email, Perfil perfil, boolean ativo) {}

  boolean administradorAtivo(UUID id);

  List<Dados> listar(int offset, int limite);

  Optional<Dados> buscar(UUID id);

  Dados criar(UUID id, String email, String senhaHash, Perfil perfil);

  Optional<Dados> atualizar(UUID id, String email, Perfil perfil, boolean ativo);

  boolean desativar(UUID id);

  /** Resultado da exclusão definitiva: só ocorre para conta desativada. */
  enum Exclusao {
    EXCLUIDO,
    NAO_ENCONTRADO,
    NAO_INATIVO
  }

  Exclusao excluir(UUID id);
}
