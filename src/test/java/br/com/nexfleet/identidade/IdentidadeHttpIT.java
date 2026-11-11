package br.com.nexfleet.identidade;

import static org.junit.jupiter.api.Assertions.*;

import br.com.nexfleet.identidade.gerenciarusuarios.application.Usuarios;
import br.com.nexfleet.identidade.usuario.domain.Perfil;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import tools.jackson.databind.json.JsonMapper;

/** Contrato HTTP da aplicação inteira com os usuários gravados no SQL Server. */
class IdentidadeHttpIT extends SqlServerIT {
  static final String SENHA = "senha-do-teste-it";

  @LocalServerPort int porta;
  @Autowired Usuarios usuarios;
  @Autowired JdbcTemplate jdbc;
  final HttpClient http = HttpClient.newHttpClient();
  final JsonMapper json = JsonMapper.builder().build();

  @AfterEach
  void limpar() {
    jdbc.update("DELETE FROM identidade.usuario");
  }

  @Test
  void loginComUsuarioGravadoNoSqlServer() throws Exception {
    var id = gravar("motorista.chefe@example.com", Perfil.GESTOR_FROTA);
    var login = login(" Motorista.Chefe@Example.com ", SENHA);
    assertEquals(200, login.statusCode());
    var token = json.readTree(login.body()).get("accessToken").asString();
    var me = get("/api/v1/identidade/me", token);
    assertEquals(200, me.statusCode());
    assertEquals(id.toString(), json.readTree(me.body()).get("id").asString());
    assertEquals(401, login("motorista.chefe@example.com", "senha-errada-do-it").statusCode());
  }

  @Test
  void emailDuplicadoComCaixaDiferenteResponde409() throws Exception {
    gravar("admin@example.com", Perfil.ADMINISTRADOR);
    var token =
        json.readTree(login("admin@example.com", SENHA).body()).get("accessToken").asString();
    assertEquals(201, cadastrar(token, "repetido@example.com").statusCode());
    assertEquals(409, cadastrar(token, " REPETIDO@Example.COM ").statusCode());
    assertEquals(409, cadastrar(token, "Admin@Example.com").statusCode());
  }

  @Test
  void readinessEInfoUpComBancoDisponivel() throws Exception {
    assertEquals(200, get("/actuator/health/readiness", null).statusCode());
    var info = get("/api/v1/identidade/info", null);
    assertEquals(200, info.statusCode());
    assertEquals("UP", json.readTree(info.body()).get("status").asString());
  }

  private UUID gravar(String email, Perfil perfil) {
    var id = UUID.randomUUID();
    usuarios.criar(id, email, new BCryptPasswordEncoder(4).encode(SENHA), perfil);
    return id;
  }

  private HttpResponse<String> login(String email, String senha) throws Exception {
    return post(
        "/api/v1/identidade/login",
        null,
        "{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}");
  }

  private HttpResponse<String> cadastrar(String token, String email) throws Exception {
    return post(
        "/api/v1/identidade/usuarios",
        token,
        "{\"email\":\"" + email + "\",\"senha\":\"senha-nova-segura\",\"perfil\":\"GESTOR_FROTA\"}");
  }

  private HttpResponse<String> post(String caminho, String token, String corpo) throws Exception {
    var req =
        HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(corpo));
    if (token != null) req.header("Authorization", "Bearer " + token);
    return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> get(String caminho, String token) throws Exception {
    var req = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho)).GET();
    if (token != null) req.header("Authorization", "Bearer " + token);
    return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
  }
}
