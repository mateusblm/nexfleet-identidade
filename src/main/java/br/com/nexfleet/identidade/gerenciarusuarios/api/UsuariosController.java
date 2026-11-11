package br.com.nexfleet.identidade.gerenciarusuarios.api;

import br.com.nexfleet.identidade.gerenciarusuarios.application.*;
import br.com.nexfleet.identidade.usuario.domain.Perfil;
import java.net.URI;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/identidade/usuarios")
public class UsuariosController {
  private final GerenciarUsuarios usuarios;

  public UsuariosController(GerenciarUsuarios usuarios) {
    this.usuarios = usuarios;
  }

  public record Criar(String email, String senha, Perfil perfil) {
    @Override
    public String toString() {
      return "CriarUsuario[redigido]";
    }
  }

  public record Atualizar(String email, Perfil perfil, Boolean ativo) {}

  @GetMapping
  public ResponseEntity<List<Usuarios.Dados>> listar(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "20") int tamanho) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(usuarios.listar(autor(jwt), pagina, tamanho));
  }

  @GetMapping("/{id}")
  public ResponseEntity<Usuarios.Dados> consultar(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(usuarios.consultar(autor(jwt), id));
  }

  @PostMapping
  public ResponseEntity<Usuarios.Dados> criar(
      @AuthenticationPrincipal Jwt jwt, @RequestBody Criar entrada) {
    var novo = usuarios.criar(autor(jwt), entrada.email(), entrada.senha(), entrada.perfil());
    return ResponseEntity.created(URI.create("/api/v1/identidade/usuarios/" + novo.id()))
        .cacheControl(CacheControl.noStore())
        .body(novo);
  }

  @PutMapping("/{id}")
  public ResponseEntity<Usuarios.Dados> atualizar(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestBody Atualizar entrada) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            usuarios.atualizar(autor(jwt), id, entrada.email(), entrada.perfil(), entrada.ativo()));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> desativar(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    usuarios.desativar(autor(jwt), id);
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }

  private UUID autor(Jwt jwt) {
    try {
      return UUID.fromString(jwt.getSubject());
    } catch (IllegalArgumentException e) {
      throw new FalhaUsuario(FalhaUsuario.Tipo.PROIBIDO, "Identidade inválida.");
    }
  }

  @ExceptionHandler(FalhaUsuario.class)
  public ResponseEntity<Map<String, String>> falha(FalhaUsuario error) {
    int status =
        switch (error.tipo()) {
          case PROIBIDO -> 403;
          case INVALIDO -> 422;
          case NAO_ENCONTRADO -> 404;
          case CONFLITO -> 409;
        };
    return ResponseEntity.status(status)
        .cacheControl(CacheControl.noStore())
        .body(Map.of("message", error.getMessage()));
  }
}
