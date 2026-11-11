package br.com.nexfleet.identidade.gerenciarusuarios.application;

public interface ProtegerSenha {
  String hash(String senha);
}
