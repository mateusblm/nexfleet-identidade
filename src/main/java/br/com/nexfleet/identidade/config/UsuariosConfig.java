package br.com.nexfleet.identidade.config;

import br.com.nexfleet.identidade.gerenciarusuarios.application.*;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class UsuariosConfig {
  @Bean
  GerenciarUsuarios gerenciarUsuarios(Usuarios usuarios) {
    var bcrypt = new BCryptPasswordEncoder(12);
    return new GerenciarUsuarios(usuarios, bcrypt::encode);
  }
}
