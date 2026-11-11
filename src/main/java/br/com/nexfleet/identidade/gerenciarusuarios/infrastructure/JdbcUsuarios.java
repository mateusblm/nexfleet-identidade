package br.com.nexfleet.identidade.gerenciarusuarios.infrastructure;

import br.com.nexfleet.identidade.gerenciarusuarios.application.*;
import br.com.nexfleet.identidade.usuario.domain.Perfil;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcUsuarios implements Usuarios {
  private final JdbcTemplate jdbc;
  private static final RowMapper<Dados> MAP =
      (rs, row) ->
          new Dados(
              rs.getObject("id", UUID.class), rs.getString("email"),
              Perfil.valueOf(rs.getString("perfil")), rs.getBoolean("ativo"));

  public JdbcUsuarios(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean administradorAtivo(UUID id) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM identidade.usuario WHERE id=? AND ativo AND"
                + " perfil='ADMINISTRADOR')",
            Boolean.class,
            id));
  }

  @Override
  public List<Dados> listar(int offset, int limite) {
    return jdbc.query(
        "SELECT id,email,perfil,ativo FROM identidade.usuario ORDER BY email,id LIMIT ? OFFSET ?",
        MAP,
        limite,
        offset);
  }

  @Override
  public Optional<Dados> buscar(UUID id) {
    return jdbc
        .query("SELECT id,email,perfil,ativo FROM identidade.usuario WHERE id=?", MAP, id)
        .stream()
        .findFirst();
  }

  @Override
  public Dados criar(UUID id, String email, String hash, Perfil perfil) {
    try {
      return jdbc.queryForObject(
          "INSERT INTO identidade.usuario(id,email,senha_hash,perfil) VALUES (?,?,?,?) RETURNING"
              + " id,email,perfil,ativo",
          MAP,
          id,
          email,
          hash,
          perfil.name());
    } catch (DuplicateKeyException e) {
      throw duplicado();
    }
  }

  @Override
  public Optional<Dados> atualizar(UUID id, String email, Perfil perfil, boolean ativo) {
    try {
      return jdbc
          .query(
              "UPDATE identidade.usuario SET email=?,perfil=?,ativo=? WHERE id=? RETURNING"
                  + " id,email,perfil,ativo",
              MAP,
              email,
              perfil.name(),
              ativo,
              id)
          .stream()
          .findFirst();
    } catch (DuplicateKeyException e) {
      throw duplicado();
    }
  }

  @Override
  public boolean desativar(UUID id) {
    return jdbc.update("UPDATE identidade.usuario SET ativo=false WHERE id=?", id) == 1;
  }

  @Override
  public Exclusao excluir(UUID id) {
    if (jdbc.update("DELETE FROM identidade.usuario WHERE id=? AND NOT ativo", id) == 1)
      return Exclusao.EXCLUIDO;
    return buscar(id).isPresent() ? Exclusao.NAO_INATIVO : Exclusao.NAO_ENCONTRADO;
  }

  private static FalhaUsuario duplicado() {
    return new FalhaUsuario(FalhaUsuario.Tipo.CONFLITO, "E-mail já cadastrado.");
  }
}
