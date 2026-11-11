package br.com.nexfleet.identidade.autenticar.application;

import java.util.Optional;

public interface BuscarUsuario {
  Optional<Usuario> porEmail(String email);
}
