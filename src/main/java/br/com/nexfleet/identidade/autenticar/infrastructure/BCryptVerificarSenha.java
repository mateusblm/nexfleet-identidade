package br.com.nexfleet.identidade.autenticar.infrastructure;

import br.com.nexfleet.identidade.autenticar.application.VerificarSenha;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class BCryptVerificarSenha implements VerificarSenha {
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
  private final String hashAusente = encoder.encode(java.util.UUID.randomUUID().toString());

  @Override
  public boolean confere(String senha, String hash) {
    boolean confere = encoder.matches(senha, hash == null ? hashAusente : hash);
    return hash != null && confere;
  }
}
