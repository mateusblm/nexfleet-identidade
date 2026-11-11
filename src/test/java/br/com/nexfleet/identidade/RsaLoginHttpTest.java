package br.com.nexfleet.identidade;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import br.com.nexfleet.identidade.autenticar.api.*;
import br.com.nexfleet.identidade.autenticar.application.*;
import br.com.nexfleet.identidade.config.*;
import java.nio.file.*;
import java.security.KeyPairGenerator;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.*;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ActiveProfiles("jwt-rsa")
@ContextConfiguration(classes = RsaLoginHttpTest.Context.class)
class RsaLoginHttpTest {
  static Path privateKey;

  @Configuration
  @EnableWebMvc
  @EnableWebSecurity
  @Import({
    AuthConfig.class,
    RsaJwtConfig.class,
    SecurityConfig.class,
    LoginController.class,
    JwksController.class
  })
  static class Context {
    @Bean
    BuscarUsuario usuarios() {
      var hash =
          new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12)
              .encode("senha-teste-rsa");
      var user = new Usuario(UUID.randomUUID(), "rsa@example.com", hash, true);
      return email -> email.equals(user.email()) ? Optional.of(user) : Optional.empty();
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) throws Exception {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    var encoded = generator.generateKeyPair().getPrivate().getEncoded();
    privateKey = Files.createTempFile("nexfleet-rsa-test-", ".pem");
    Files.writeString(
        privateKey,
        "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded)
            + "\n-----END PRIVATE KEY-----\n");
    registry.add("auth.jwt.private-key", () -> privateKey.toUri().toString());
    // Sem auth.jwt.secret: o perfil RSA não pode exigir o segredo legado.
  }

  @AfterAll
  static void cleanup() throws Exception {
    if (privateKey != null) Files.deleteIfExists(privateKey);
  }

  @Autowired WebApplicationContext context;
  @Autowired EmitirToken tokens;
  MockMvc mvc;

  @BeforeEach
  void setup() {
    mvc = webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void jwksPublicoNaoExpoeMaterialPrivado() throws Exception {
    mvc.perform(get("/.well-known/jwks.json"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
        .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
        .andExpect(jsonPath("$.keys[0].kid").isString())
        .andExpect(jsonPath("$.keys[0].n").isString())
        .andExpect(jsonPath("$.keys[0].e").isString())
        .andExpect(jsonPath("$.keys[0].d").doesNotExist())
        .andExpect(jsonPath("$.keys[0].p").doesNotExist())
        .andExpect(jsonPath("$.keys[0].q").doesNotExist());
  }

  @Test
  void perfilRsaPreservaContratoDeLogin() throws Exception {
    mvc.perform(
            post("/api/v1/identidade/login")
                .contentType("application/json")
                .content("{\"email\":\"rsa@example.com\",\"senha\":\"senha-teste-rsa\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.expiresIn").value(900))
        .andExpect(header().string("Cache-Control", "no-store"));
  }

  @Test
  void meAceitaTokenRsaENegaAusenciaDeToken() throws Exception {
    var id = UUID.randomUUID();
    mvc.perform(
            get("/api/v1/identidade/me")
                .header("Authorization", "Bearer " + tokens.emitir(id).accessToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id.toString()));
    mvc.perform(get("/api/v1/identidade/me")).andExpect(status().isUnauthorized());
  }
}
