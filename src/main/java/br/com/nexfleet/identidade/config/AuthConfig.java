package br.com.nexfleet.identidade.config;

import br.com.nexfleet.identidade.autenticar.application.*;
import br.com.nexfleet.identidade.autenticar.infrastructure.*;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

@Configuration
public class AuthConfig {
  @Bean
  @Profile("!jwt-rsa")
  SecretKey jwtKey(@Value("${auth.jwt.secret}") String secret) {
    byte[] bytes = Base64.getDecoder().decode(secret);
    if (bytes.length < 32)
      throw new IllegalArgumentException(
          "JWT_SECRET deve conter ao menos 32 bytes aleatórios em Base64.");
    return new SecretKeySpec(bytes, "HmacSHA256");
  }

  @Bean
  @Profile("!jwt-rsa")
  JwtEncoder jwtEncoder(SecretKey key) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(key));
  }

  @Bean
  @Profile("!jwt-rsa")
  JwtDecoder jwtDecoder(SecretKey key) {
    var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    OAuth2TokenValidator<Jwt> audience =
        jwt ->
            jwt.getAudience().contains(JwtEmitirToken.AUDIENCE)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(JwtEmitirToken.ISSUER), audience));
    return decoder;
  }

  @Bean
  VerificarSenha verificarSenha() {
    return new BCryptVerificarSenha();
  }

  @Bean
  @Profile("!jwt-rsa")
  EmitirToken emitirToken(JwtEncoder encoder) {
    return new JwtEmitirToken(encoder, Clock.systemUTC());
  }

  @Bean
  Autenticar autenticar(BuscarUsuario usuarios, VerificarSenha senhas, EmitirToken tokens) {
    return new Autenticar(usuarios, senhas, tokens);
  }
}
