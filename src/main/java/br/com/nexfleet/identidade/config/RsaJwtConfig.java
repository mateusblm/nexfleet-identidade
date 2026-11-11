package br.com.nexfleet.identidade.config;

import br.com.nexfleet.identidade.autenticar.application.EmitirToken;
import br.com.nexfleet.identidade.autenticar.infrastructure.JwtEmitirToken;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;

@Configuration
@Profile("jwt-rsa")
public class RsaJwtConfig {
  @Bean
  RSAKey signingKey(@Value("${auth.jwt.private-key}") Resource resource) throws Exception {
    // A chave pública é derivada da privada: não há dois arquivos que possam divergir.
    try (var input = resource.getInputStream()) {
      var privateKey = (RSAPrivateCrtKey) RsaKeyConverters.pkcs8().convert(input);
      if (privateKey == null || privateKey.getModulus().bitLength() < 2048) {
        throw new IllegalArgumentException("A chave RSA deve ter pelo menos 2048 bits.");
      }
      var publicKey =
          (RSAPublicKey)
              KeyFactory.getInstance("RSA")
                  .generatePublic(
                      new RSAPublicKeySpec(
                          privateKey.getModulus(), privateKey.getPublicExponent()));
      return new RSAKey.Builder(publicKey)
          .privateKey(privateKey)
          .keyUse(KeyUse.SIGNATURE)
          .algorithm(JWSAlgorithm.RS256)
          .keyIDFromThumbprint()
          .build();
    }
  }

  @Bean
  JwtEncoder rsaEncoder(RSAKey key) {
    return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
  }

  @Bean
  JwtDecoder rsaDecoder(RSAKey key) throws Exception {
    var decoder =
        NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey())
            .signatureAlgorithm(SignatureAlgorithm.RS256)
            .build();
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
  EmitirToken rsaTokens(JwtEncoder encoder, RSAKey key) {
    return new JwtEmitirToken(
        encoder,
        Clock.systemUTC(),
        JwsHeader.with(SignatureAlgorithm.RS256).keyId(key.getKeyID()).build());
  }
}
