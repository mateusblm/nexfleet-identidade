package br.com.nexfleet.identidade.autenticar.api;

import br.com.nexfleet.identidade.autenticar.application.Autenticar;
import br.com.nexfleet.identidade.autenticar.application.EmitirToken;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class LoginController {
  private final Autenticar autenticar;

  public LoginController(Autenticar autenticar) {
    this.autenticar = autenticar;
  }

  public record Credenciais(String email, String senha) {
    @Override
    public String toString() {
      return "Credenciais[redigido]";
    }
  }

  @PostMapping("/api/v1/identidade/login")
  public ResponseEntity<EmitirToken.Token> login(@RequestBody Credenciais credenciais) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(autenticar.executar(credenciais.email(), credenciais.senha()));
  }

  @GetMapping("/api/v1/identidade/me")
  public Map<String, String> me(@AuthenticationPrincipal Jwt jwt) {
    return Map.of(
        "id",
        jwt.getSubject(),
        "perfil",
        java.util.Objects.requireNonNullElse(jwt.getClaimAsString("perfil"), "GESTOR_FROTA"));
  }

  @ExceptionHandler(Autenticar.CredenciaisInvalidas.class)
  public ResponseEntity<Map<String, String>> credenciaisInvalidas() {
    return ResponseEntity.status(401)
        .cacheControl(CacheControl.noStore())
        .body(Map.of("message", "Credenciais inválidas."));
  }
}
