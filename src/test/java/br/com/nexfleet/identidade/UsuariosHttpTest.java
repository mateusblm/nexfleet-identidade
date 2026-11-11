package br.com.nexfleet.identidade;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import br.com.nexfleet.identidade.autenticar.application.*;
import br.com.nexfleet.identidade.config.*;
import br.com.nexfleet.identidade.gerenciarusuarios.api.UsuariosController;
import br.com.nexfleet.identidade.gerenciarusuarios.application.*;
import br.com.nexfleet.identidade.usuario.domain.Perfil;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = UsuariosHttpTest.Context.class)
class UsuariosHttpTest {
  static final String PATH = "/api/v1/identidade/usuarios";
  static final UUID ADMIN = UUID.randomUUID();
  static final UUID USER = UUID.randomUUID();
  static final Usuarios.Dados DATA =
      new Usuarios.Dados(USER, "novo@example.com", Perfil.GESTOR_FROTA, true);

  @Configuration
  @EnableWebMvc
  @EnableWebSecurity
  @Import({AuthConfig.class, SecurityConfig.class, UsuariosConfig.class, UsuariosController.class})
  static class Context {
    @Bean
    Usuarios usuarios() {
      return mock(Usuarios.class);
    }

    @Bean
    BuscarUsuario busca() {
      return email -> Optional.empty();
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    byte[] key = new byte[32];
    new java.security.SecureRandom().nextBytes(key);
    registry.add("auth.jwt.secret", () -> Base64.getEncoder().encodeToString(key));
  }

  @Autowired WebApplicationContext context;
  @Autowired Usuarios store;
  @Autowired EmitirToken tokens;
  MockMvc mvc;
  String admin;

  @BeforeEach
  void setup() {
    reset(store);
    when(store.administradorAtivo(ADMIN)).thenReturn(true);
    admin = "Bearer " + tokens.emitir(ADMIN, Perfil.ADMINISTRADOR).accessToken();
    mvc = webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void semToken401EGestor403SemConsultarBanco() throws Exception {
    mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    var gestor = "Bearer " + tokens.emitir(USER, Perfil.GESTOR_FROTA).accessToken();
    mvc.perform(get(PATH).header("Authorization", gestor)).andExpect(status().isForbidden());
    mvc.perform(
            post(PATH)
                .header("Authorization", gestor)
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            put(PATH + "/" + USER)
                .header("Authorization", gestor)
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isForbidden());
    mvc.perform(delete(PATH + "/" + USER).header("Authorization", gestor))
        .andExpect(status().isForbidden());
    verifyNoInteractions(store);
  }

  @Test
  void tokenDeAdministradorRebaixadoNaoAutoriza() throws Exception {
    when(store.administradorAtivo(ADMIN)).thenReturn(false);
    mvc.perform(get(PATH).header("Authorization", admin)).andExpect(status().isForbidden());
    verify(store, never()).listar(anyInt(), anyInt());
  }

  @Test
  void cadastraComBcryptSemRetornarCredenciais() throws Exception {
    when(store.criar(any(), eq("novo@example.com"), anyString(), eq(Perfil.GESTOR_FROTA)))
        .thenAnswer(
            call -> {
              assertTrue(
                  new BCryptPasswordEncoder().matches("senha-nova-segura", call.getArgument(2)));
              return DATA;
            });
    mvc.perform(
            post(PATH)
                .header("Authorization", admin)
                .contentType("application/json")
                .content(
                    "{\"email\":\" NOVO@example.com"
                        + " \",\"senha\":\"senha-nova-segura\",\"perfil\":\"GESTOR_FROTA\"}"))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", PATH + "/" + USER))
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.email").value("novo@example.com"))
        .andExpect(jsonPath("$.senha").doesNotExist())
        .andExpect(jsonPath("$.senhaHash").doesNotExist());
  }

  @Test
  void listaPaginaEConsultaIndividual() throws Exception {
    when(store.listar(20, 20)).thenReturn(List.of(DATA));
    when(store.buscar(USER)).thenReturn(Optional.of(DATA));
    mvc.perform(get(PATH + "?pagina=1&tamanho=20").header("Authorization", admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(USER.toString()));
    mvc.perform(get(PATH + "/" + USER).header("Authorization", admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.perfil").value("GESTOR_FROTA"));
  }

  @Test
  void editaEDesativaSemExclusaoFisica() throws Exception {
    when(store.atualizar(USER, "novo@example.com", Perfil.GESTOR_FROTA, false))
        .thenReturn(
            Optional.of(new Usuarios.Dados(USER, "novo@example.com", Perfil.GESTOR_FROTA, false)));
    when(store.desativar(USER)).thenReturn(true);
    mvc.perform(
            put(PATH + "/" + USER)
                .header("Authorization", admin)
                .contentType("application/json")
                .content(
                    "{\"email\":\"novo@example.com\",\"perfil\":\"GESTOR_FROTA\",\"ativo\":false}"))
        .andExpect(status().isOk());
    mvc.perform(delete(PATH + "/" + USER).header("Authorization", admin))
        .andExpect(status().isNoContent());
    verify(store).desativar(USER);
  }

  @Test
  void emailDuplicado409UsuarioAusente404ECampoInvalido422() throws Exception {
    when(store.criar(any(), anyString(), anyString(), any()))
        .thenThrow(new FalhaUsuario(FalhaUsuario.Tipo.CONFLITO, "E-mail já cadastrado."));
    mvc.perform(
            post(PATH)
                .header("Authorization", admin)
                .contentType("application/json")
                .content(
                    "{\"email\":\"novo@example.com\",\"senha\":\"senha-nova-segura\",\"perfil\":\"GESTOR_FROTA\"}"))
        .andExpect(status().isConflict());
    when(store.buscar(USER)).thenReturn(Optional.empty());
    mvc.perform(get(PATH + "/" + USER).header("Authorization", admin))
        .andExpect(status().isNotFound());
    mvc.perform(
            post(PATH)
                .header("Authorization", admin)
                .contentType("application/json")
                .content(
                    "{\"email\":\"invalido\",\"senha\":\"curta\",\"perfil\":\"GESTOR_FROTA\"}"))
        .andExpect(status().isUnprocessableContent());
  }
}
