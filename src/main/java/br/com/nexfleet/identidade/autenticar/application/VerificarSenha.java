package br.com.nexfleet.identidade.autenticar.application;

public interface VerificarSenha {
  boolean confere(String senha, String hash);
}
