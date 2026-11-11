package br.com.nexfleet.identidade;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/** O OpenAPI é público (lido pela Swagger UI do Gateway); as operações continuam exigindo JWT. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.url=jdbc:sqlserver://127.0.0.1:1;databaseName=nexfleet;encrypt=false;loginTimeout=1",
      "spring.datasource.username=identidade",
      "spring.datasource.password=indisponivel",
      "spring.flyway.enabled=false",
      "auth.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    })
class OpenApiPublicoTest {
  @LocalServerPort int porta;
  final HttpClient http = HttpClient.newHttpClient();

  @Test
  void openApiRespondeSemTokenComOsPathsDoServico() throws Exception {
    var resposta = get("/v3/api-docs");
    assertEquals(200, resposta.statusCode());
    String corpo = resposta.body();
    assertTrue(corpo.contains("\"version\":\"1.0.0\""), corpo);
    assertTrue(corpo.contains("\"servers\":[{\"url\":\"/\""), corpo);
    assertTrue(corpo.contains("\"bearer-jwt\":{\"type\":\"http\""), corpo);
    for (String path :
        new String[] {
          "/api/v1/identidade/login", "/api/v1/identidade/me", "/api/v1/identidade/usuarios"
        }) assertTrue(corpo.contains("\"" + path + "\":"), path);
  }

  @Test
  void operacaoDeNegocioContinuaRespondendo401SemToken() throws Exception {
    assertEquals(401, get("/api/v1/identidade/me").statusCode());
  }

  private HttpResponse<String> get(String caminho) throws Exception {
    return http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
  }
}
