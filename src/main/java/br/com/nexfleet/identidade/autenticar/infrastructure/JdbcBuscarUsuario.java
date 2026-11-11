package br.com.nexfleet.identidade.autenticar.infrastructure;

import br.com.nexfleet.identidade.autenticar.application.BuscarUsuario;
import br.com.nexfleet.identidade.autenticar.application.Usuario;
import br.com.nexfleet.identidade.usuario.domain.Perfil;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcBuscarUsuario implements BuscarUsuario {
  private final JdbcTemplate jdbc;

  public JdbcBuscarUsuario(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<Usuario> porEmail(String email) {
    return jdbc
        .query(
            "SELECT id, email, senha_hash, ativo, perfil FROM identidade.usuario WHERE email = ?",
            (rs, row) ->
                new Usuario(
                    rs.getObject("id", UUID.class),
                    rs.getString("email"),
                    rs.getString("senha_hash"),
                    rs.getBoolean("ativo"),
                    Perfil.valueOf(rs.getString("perfil"))),
            email)
        .stream()
        .findFirst();
  }
}
