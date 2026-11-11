package br.com.nexfleet.identidade;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/** Com o SQL Server inacessível, a instância sai do balanceamento em vez de responder erro. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.url=jdbc:sqlserver://127.0.0.1:1;databaseName=nexfleet;encrypt=false;loginTimeout=1",
      "spring.datasource.username=identidade",
      "spring.datasource.password=indisponivel",
      "spring.flyway.enabled=false",
      "auth.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    })
class ReadinessSemBancoIT {
  @LocalServerPort int porta;
  final HttpClient http = HttpClient.newHttpClient();

  @Test
  void readinessEInfoRespondemDown503() throws Exception {
    var readiness = get("/actuator/health/readiness");
    assertEquals(503, readiness.statusCode());
    assertTrue(readiness.body().contains("\"status\":\"DOWN\""), readiness.body());
    var info = get("/api/v1/identidade/info");
    assertEquals(503, info.statusCode());
    assertTrue(info.body().contains("\"status\":\"DOWN\""), info.body());
  }

  private HttpResponse<String> get(String caminho) throws Exception {
    return http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
  }
}
