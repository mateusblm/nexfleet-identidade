package br.com.nexfleet.identidade.autenticar.api;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("jwt-rsa")
public class JwksController {
  private final Map<String, Object> publicKeys;

  public JwksController(RSAKey key) {
    publicKeys = new JWKSet(key.toPublicJWK()).toJSONObject();
  }

  @GetMapping("/.well-known/jwks.json")
  public Map<String, Object> keys() {
    return publicKeys;
  }
}
