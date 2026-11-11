package br.com.nexfleet.identidade;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

/**
 * Aplicação inteira contra um SQL Server 2022 real. O contêiner é único na JVM e o contexto do
 * Spring é reaproveitado entre as classes que herdam daqui.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class SqlServerIT {
  @ServiceConnection
  static final MSSQLServerContainer SQL_SERVER =
      new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-latest")
          .acceptLicense()
          .withInitScript("sqlserver/bootstrap-identidade.sql");

  private static final String JWT_SECRET;

  static {
    byte[] segredo = new byte[32];
    new SecureRandom().nextBytes(segredo);
    JWT_SECRET = Base64.getEncoder().encodeToString(segredo);
    SQL_SERVER.start();
  }

  @DynamicPropertySource
  static void propriedades(DynamicPropertyRegistry registry) {
    registry.add("auth.jwt.secret", () -> JWT_SECRET);
  }
}
