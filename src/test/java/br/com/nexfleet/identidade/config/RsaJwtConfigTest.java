package br.com.nexfleet.identidade.config;

import static org.junit.jupiter.api.Assertions.*;

import br.com.nexfleet.identidade.autenticar.api.JwksController;
import br.com.nexfleet.identidade.autenticar.infrastructure.JwtEmitirToken;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;

class RsaJwtConfigTest {
  final RsaJwtConfig config = new RsaJwtConfig();

  public static byte[] pem(int bits) throws Exception {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(bits);
    var key = generator.generateKeyPair().getPrivate();
    return ("-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(key.getEncoded())
            + "\n-----END PRIVATE KEY-----\n")
        .getBytes(StandardCharsets.US_ASCII);
  }

  RSAKey key() throws Exception {
    return config.signingKey(new ByteArrayResource(pem(2048)));
  }

  @Test
  void assinaComRsaEPublicaSomenteChavePublica() throws Exception {
    var key = key();
    var id = UUID.randomUUID();
    var token = config.rsaTokens(config.rsaEncoder(key), key).emitir(id);
    var set = JWKSet.parse(new JwksController(key).keys());
    assertEquals(1, set.getKeys().size());
    var publicKey = (RSAKey) set.getKeys().getFirst();
    assertFalse(publicKey.isPrivate());
    assertNull(publicKey.getPrivateExponent());
    assertEquals(key.getKeyID(), publicKey.getKeyID());
    // O consumidor recebe apenas o conteúdo do endpoint, sem acesso à chave privada.
    var jwt = config.rsaDecoder(publicKey).decode(token.accessToken());
    assertEquals(id.toString(), jwt.getSubject());
    assertEquals("RS256", jwt.getHeaders().get("alg"));
    assertEquals("GESTOR_FROTA", jwt.getClaimAsString("perfil"));
    assertEquals(key.getKeyID(), jwt.getHeaders().get("kid"));
    assertEquals(900, Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).toSeconds());
  }

  @Test
  void recusaAssinaturaDeOutraChaveETokenHmac() throws Exception {
    var key = key();
    var other = key();
    var decoder = config.rsaDecoder(key);
    var foreign = config.rsaTokens(config.rsaEncoder(other), other).emitir(UUID.randomUUID());
    assertThrows(JwtException.class, () -> decoder.decode(foreign.accessToken()));
    var legacy = new AuthConfig();
    var secret = legacy.jwtKey(Base64.getEncoder().encodeToString(AuthConfigTest.randomBytes()));
    var old = legacy.emitirToken(legacy.jwtEncoder(secret)).emitir(UUID.randomUUID());
    assertThrows(JwtException.class, () -> decoder.decode(old.accessToken()));
  }

  @Test
  void recusaExpiracaoEmissorEAudienciaInvalidos() throws Exception {
    var key = key();
    var encoder = config.rsaEncoder(key);
    var decoder = config.rsaDecoder(key);
    for (int invalid = 0; invalid < 3; invalid++) {
      var claims =
          JwtClaimsSet.builder()
              .issuer(invalid == 0 ? "outro" : JwtEmitirToken.ISSUER)
              .audience(List.of(invalid == 1 ? "outra" : JwtEmitirToken.AUDIENCE))
              .subject(UUID.randomUUID().toString())
              .issuedAt(Instant.now().minusSeconds(3600))
              .expiresAt(Instant.now().plusSeconds(invalid == 2 ? -120 : 900))
              .build();
      var token =
          encoder.encode(
              JwtEncoderParameters.from(
                  JwsHeader.with(SignatureAlgorithm.RS256).keyId(key.getKeyID()).build(), claims));
      assertThrows(JwtException.class, () -> decoder.decode(token.getTokenValue()));
    }
  }

  @Test
  void recusaChaveFracaOuArquivoInvalido() throws Exception {
    assertThrows(
        IllegalArgumentException.class, () -> config.signingKey(new ByteArrayResource(pem(1024))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            config.signingKey(
                new ByteArrayResource("invalido".getBytes(StandardCharsets.US_ASCII))));
  }
}
