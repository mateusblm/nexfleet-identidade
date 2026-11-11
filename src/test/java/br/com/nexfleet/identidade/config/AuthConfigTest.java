package br.com.nexfleet.identidade.config;

import static org.junit.jupiter.api.Assertions.*;

import br.com.nexfleet.identidade.autenticar.infrastructure.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

class AuthConfigTest {
  final AuthConfig config = new AuthConfig();
  // Chave aleatória e descartável, exclusiva dos testes.
  final javax.crypto.SecretKey key =
      config.jwtKey(Base64.getEncoder().encodeToString(randomBytes()));

  static byte[] randomBytes() {
    byte[] bytes = new byte[32];
    new java.security.SecureRandom().nextBytes(bytes);
    return bytes;
  }

  @Test
  void bcryptConfereSenhaENegaSenhaErradaEUsuarioAusente() {
    var encoder = new BCryptPasswordEncoder(12);
    var hash = encoder.encode("uma-senha-local");
    var verifier = new BCryptVerificarSenha();
    assertNotEquals("uma-senha-local", hash);
    assertTrue(verifier.confere("uma-senha-local", hash));
    assertFalse(verifier.confere("errada", hash));
    assertFalse(verifier.confere("uma-senha-local", null));
  }

  @Test
  void emiteTokenAssinadoComSubjectIssuerAudienceEPrazo() {
    var id = UUID.randomUUID();
    var token = new JwtEmitirToken(config.jwtEncoder(key), Clock.systemUTC()).emitir(id);
    var jwt = config.jwtDecoder(key).decode(token.accessToken());
    assertEquals(id.toString(), jwt.getSubject());
    assertEquals(JwtEmitirToken.ISSUER, jwt.getIssuer().toString());
    assertEquals(List.of(JwtEmitirToken.AUDIENCE), jwt.getAudience());
    assertEquals(900, Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).toSeconds());
    assertNull(jwt.getClaim("senha"));
    assertNull(jwt.getClaim("email"));
    assertEquals("GESTOR_FROTA", jwt.getClaimAsString("perfil"));
    var admin =
        new JwtEmitirToken(config.jwtEncoder(key), Clock.systemUTC())
            .emitir(id, br.com.nexfleet.identidade.usuario.domain.Perfil.ADMINISTRADOR);
    assertEquals(
        "ADMINISTRADOR",
        config.jwtDecoder(key).decode(admin.accessToken()).getClaimAsString("perfil"));
  }

  @Test
  void rejeitaTokenExpiradoAssinaturaErradaIssuerEAudienceErrados() {
    var antigo =
        new JwtEmitirToken(
                config.jwtEncoder(key), Clock.offset(Clock.systemUTC(), Duration.ofHours(-1)))
            .emitir(UUID.randomUUID());
    assertThrows(JwtException.class, () -> config.jwtDecoder(key).decode(antigo.accessToken()));
    var outraChave = config.jwtKey(Base64.getEncoder().encodeToString(randomBytes()));
    var token =
        new JwtEmitirToken(config.jwtEncoder(outraChave), Clock.systemUTC())
            .emitir(UUID.randomUUID());
    assertThrows(JwtException.class, () -> config.jwtDecoder(key).decode(token.accessToken()));
    for (boolean issuerErrado : new boolean[] {true, false}) {
      var claims =
          JwtClaimsSet.builder()
              .issuer(issuerErrado ? "outro" : JwtEmitirToken.ISSUER)
              .audience(List.of(issuerErrado ? JwtEmitirToken.AUDIENCE : "outro"))
              .subject(UUID.randomUUID().toString())
              .issuedAt(Instant.now())
              .expiresAt(Instant.now().plusSeconds(900))
              .build();
      String invalido =
          config
              .jwtEncoder(key)
              .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
              .getTokenValue();
      assertThrows(JwtException.class, () -> config.jwtDecoder(key).decode(invalido));
    }
  }

  @Test
  void rejeitaChaveCurta() {
    assertThrows(
        IllegalArgumentException.class,
        () -> config.jwtKey(Base64.getEncoder().encodeToString(new byte[16])));
  }
}
