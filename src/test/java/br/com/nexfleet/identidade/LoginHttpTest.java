package br.com.nexfleet.identidade;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import br.com.nexfleet.identidade.autenticar.api.LoginController;
import br.com.nexfleet.identidade.autenticar.application.*;
import br.com.nexfleet.identidade.config.*;
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
@ContextConfiguration(classes = LoginHttpTest.Context.class)
class LoginHttpTest {
  @Configuration
  @EnableWebMvc
  @EnableWebSecurity
  @Import({AuthConfig.class, SecurityConfig.class, LoginController.class})
  static class Context {
    @Bean
    BuscarUsuario usuarios() {
      var hash =
          new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12)
              .encode("senha-apenas-teste");
      var usuario = new Usuario(UUID.randomUUID(), "teste@example.com", hash, true);
      return email -> email.equals(usuario.email()) ? Optional.of(usuario) : Optional.empty();
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    byte[] secret = new byte[32];
    new java.security.SecureRandom().nextBytes(secret);
    registry.add("auth.jwt.secret", () -> Base64.getEncoder().encodeToString(secret));
  }

  @Autowired WebApplicationContext context;
  @Autowired EmitirToken tokens;
  MockMvc mvc;

  @BeforeEach
  void setup() {
    mvc = webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void meSemTokenRetorna401() throws Exception {
    mvc.perform(get("/api/v1/identidade/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void tokenValidoPermiteConsultarId() throws Exception {
    var id = UUID.randomUUID();
    mvc.perform(
            get("/api/v1/identidade/me")
                .header("Authorization", "Bearer " + tokens.emitir(id).accessToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id.toString()));
  }

  @Test
  void tokenInvalidoRetorna401() throws Exception {
    mvc.perform(get("/api/v1/identidade/me").header("Authorization", "Bearer invalido"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void loginPublicoSemCsrfRetornaErroGenericoENaoCriaSessao() throws Exception {
    mvc.perform(
            post("/api/v1/identidade/login")
                .contentType("application/json")
                .content("{\"email\":\"naoexiste@example.com\",\"senha\":\"senha-incorreta\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Credenciais inválidas."))
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(result -> Assertions.assertNull(result.getRequest().getSession(false)));
  }

  @Test
  void rotaNaoAutorizadaRetorna403MesmoComToken() throws Exception {
    mvc.perform(
            get("/api/v1/identidade/admin")
                .header(
                    "Authorization", "Bearer " + tokens.emitir(UUID.randomUUID()).accessToken()))
        .andExpect(status().isForbidden());
  }

  @Test
  void jsonMalformadoRetorna400() throws Exception {
    mvc.perform(post("/api/v1/identidade/login").contentType("application/json").content("{"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void loginCorretoRetornaBearerSemExporSenhaOuHash() throws Exception {
    mvc.perform(
            post("/api/v1/identidade/login")
                .contentType("application/json")
                .content("{\"email\":\"teste@example.com\",\"senha\":\"senha-apenas-teste\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.expiresIn").value(900))
        .andExpect(jsonPath("$.accessToken").isString())
        .andExpect(jsonPath("$.senha").doesNotExist())
        .andExpect(jsonPath("$.senhaHash").doesNotExist())
        .andExpect(header().string("Cache-Control", "no-store"));
  }
}
